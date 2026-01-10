package kr.lunaf.nationSystem.service;

import kr.lunaf.nationSystem.config.WarConfig;
import kr.lunaf.nationSystem.domain.Nation;
import kr.lunaf.nationSystem.domain.NationMember;
import kr.lunaf.nationSystem.domain.NationRole;
import kr.lunaf.nationSystem.domain.WarMatchEntry;
import kr.lunaf.nationSystem.domain.WarPhase;
import kr.lunaf.nationSystem.domain.WarState;
import kr.lunaf.nationSystem.repository.NationMemberRepository;
import kr.lunaf.nationSystem.repository.NationRepository;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class WarService {
    private final WarConfig warConfig;
    private final NationRepository nationRepository;
    private final NationMemberRepository memberRepository;
    private final NationService nationService;
    private final ExecutorService dbExecutor;
    private volatile boolean matchOpen;
    private final Map<Long, WarMatchEntry> matchQueue = new ConcurrentHashMap<>();
    private final Map<Long, WarState> wars = new ConcurrentHashMap<>();
    private final Map<Long, BossBar> bossBars = new ConcurrentHashMap<>();

    public WarService(
        WarConfig warConfig,
        NationRepository nationRepository,
        NationMemberRepository memberRepository,
        NationService nationService,
        ExecutorService dbExecutor
    ) {
        this.warConfig = warConfig;
        this.nationRepository = nationRepository;
        this.memberRepository = memberRepository;
        this.nationService = nationService;
        this.dbExecutor = dbExecutor;
        this.matchOpen = warConfig.matchOpen();
    }

    public CompletableFuture<ServiceResult<Void>> enqueue(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!matchOpen) {
                return ServiceResult.failure(Status.MATCH_CLOSED);
            }
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            if (member.get().role() != NationRole.OWNER) {
                return ServiceResult.failure(Status.OWNER_ONLY);
            }
            long nationId = member.get().nationId();
            if (wars.containsKey(nationId) || matchQueue.containsKey(nationId)) {
                return ServiceResult.failure(Status.ALREADY_QUEUED);
            }
            Optional<Nation> nation = nationRepository.findById(nationId);
            if (nation.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            matchQueue.put(nationId, new WarMatchEntry(nationId, nation.get().level(), 0));
            return ServiceResult.success(null);
        }, dbExecutor);
    }

    public void tickMatching() {
        if (!matchOpen || matchQueue.isEmpty()) {
            return;
        }
        List<WarMatchEntry> entries = new ArrayList<>(matchQueue.values());
        entries.sort(Comparator.comparingInt(WarMatchEntry::waitedSeconds));
        for (WarMatchEntry entry : entries) {
            matchQueue.computeIfPresent(entry.nationId(), (id, value) ->
                new WarMatchEntry(value.nationId(), value.nationLevel(), value.waitedSeconds() + 1)
            );
        }
        entries = new ArrayList<>(matchQueue.values());
        for (WarMatchEntry entry : entries) {
            WarMatchEntry updated = matchQueue.get(entry.nationId());
            if (updated == null) {
                continue;
            }
            int allowedDiff = allowedDiff(updated.waitedSeconds());
            Optional<WarMatchEntry> opponent = entries.stream()
                .filter(other -> other.nationId() != updated.nationId())
                .filter(other -> Math.abs(updated.nationLevel() - other.nationLevel()) <= allowedDiff)
                .findFirst();
            if (opponent.isPresent()) {
                startWar(updated.nationId(), opponent.get().nationId());
                matchQueue.remove(updated.nationId());
                matchQueue.remove(opponent.get().nationId());
                break;
            }
        }
    }

    public void tickWars() {
        if (wars.isEmpty()) {
            return;
        }
        List<WarState> states = new ArrayList<>(wars.values());
        for (WarState state : states) {
            int remaining = state.remainingSeconds() - 1;
            WarPhase phase = state.phase();
            if (remaining <= 0) {
                endWar(state.nationA(), state.nationB(), "무승부");
                continue;
            }
            if (phase == WarPhase.PREPARE && remaining <= warConfig.battleSeconds()) {
                phase = WarPhase.ACTIVE;
                sendBoth(state, "&6[Nation] &f전쟁이 시작되었습니다!");
            }
            WarState updated = new WarState(state.nationA(), state.nationB(), phase, remaining);
            wars.put(state.nationA(), updated);
            wars.put(state.nationB(), updated);
            updateBossBar(updated);
            notifyCountdown(updated);
        }
    }

    public boolean isInWar(long nationId) {
        return wars.containsKey(nationId);
    }

    public boolean isMatchOpen() {
        return matchOpen;
    }

    public void setMatchOpen(boolean open) {
        this.matchOpen = open;
    }

    public Optional<WarState> getWarState(long nationId) {
        return Optional.ofNullable(wars.get(nationId));
    }

    public void removeFromQueue(long nationId) {
        matchQueue.remove(nationId);
        BossBar bar = bossBars.remove(nationId);
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void clearMatching() {
        for (Long nationId : new ArrayList<>(matchQueue.keySet())) {
            removeFromQueue(nationId);
        }
    }

    private void startWar(long nationA, long nationB) {
        int total = warConfig.prepareSeconds() + warConfig.battleSeconds();
        WarState state = new WarState(nationA, nationB, WarPhase.PREPARE, total);
        wars.put(nationA, state);
        wars.put(nationB, state);
        sendBoth(state, "&6[Nation] &f전쟁 매칭이 완료되었습니다.");
        updateBossBar(state);
    }

    private void endWar(long nationA, long nationB, String reason) {
        WarState state = wars.remove(nationA);
        wars.remove(nationB);
        if (state == null) {
            return;
        }
        sendBoth(state, "&6[Nation] &f전쟁 종료: &e" + reason);
        removeBossBar(nationA);
        removeBossBar(nationB);
    }

    private void sendBoth(WarState state, String message) {
        nationService.sendNationMessage(state.nationA(), message);
        nationService.sendNationMessage(state.nationB(), message);
    }

    private int allowedDiff(int waitedSeconds) {
        for (WarConfig.MatchThreshold threshold : warConfig.matchThresholds()) {
            if (waitedSeconds <= threshold.timeSeconds()) {
                return threshold.maxLevelDiff();
            }
        }
        return warConfig.matchThresholds().get(warConfig.matchThresholds().size() - 1).maxLevelDiff();
    }

    private void updateBossBar(WarState state) {
        BossBar barA = bossBars.computeIfAbsent(state.nationA(), key ->
            Bukkit.createBossBar("War", BarColor.RED, BarStyle.SOLID)
        );
        BossBar barB = bossBars.computeIfAbsent(state.nationB(), key ->
            Bukkit.createBossBar("War", BarColor.RED, BarStyle.SOLID)
        );
        String title = warTitle(state);
        barA.setTitle(title);
        barB.setTitle(title);
        double progress = Math.max(0.0, Math.min(1.0,
            state.remainingSeconds() / (double) (warConfig.prepareSeconds() + warConfig.battleSeconds())));
        barA.setProgress(progress);
        barB.setProgress(progress);
        attachPlayers(state.nationA(), barA);
        attachPlayers(state.nationB(), barB);
    }

    private void attachPlayers(long nationId, BossBar bar) {
        for (UUID memberUuid : memberRepository.listMemberUuids(nationId)) {
            Player player = Bukkit.getPlayer(memberUuid);
            if (player != null && !bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
    }

    private void removeBossBar(long nationId) {
        BossBar bar = bossBars.remove(nationId);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private String warTitle(WarState state) {
        if (state.phase() == WarPhase.PREPARE) {
            return "전쟁 준비 중... (" + state.remainingSeconds() + "초)";
        }
        return "전쟁 진행 중... (" + state.remainingSeconds() + "초)";
    }

    private void notifyCountdown(WarState state) {
        int remaining = state.remainingSeconds();
        if (state.phase() == WarPhase.PREPARE) {
            if (remaining == warConfig.battleSeconds() + 120) {
                sendBoth(state, "&6[Nation] &f전쟁이 2분 뒤 시작됩니다.");
            } else if (remaining == warConfig.battleSeconds() + 60) {
                sendBoth(state, "&6[Nation] &f전쟁이 1분 뒤 시작됩니다.");
            } else if (remaining == warConfig.battleSeconds() + 10) {
                sendBoth(state, "&6[Nation] &f전쟁이 10초 뒤 시작됩니다.");
            }
        }
    }

    public enum Status {
        SUCCESS,
        NOT_IN_NATION,
        OWNER_ONLY,
        ALREADY_QUEUED,
        MATCH_CLOSED,
        ERROR
    }

    public record ServiceResult<T>(Status status, T data) {
        public static <T> ServiceResult<T> success(T data) {
            return new ServiceResult<>(Status.SUCCESS, data);
        }

        public static <T> ServiceResult<T> failure(Status status) {
            return new ServiceResult<>(status, null);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }
}
