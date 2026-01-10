package kr.lunaf.nationSystem.service;

import kr.lunaf.nationSystem.config.QuestsConfig;
import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.DailyQuest;
import kr.lunaf.nationSystem.domain.DailyQuestType;
import kr.lunaf.nationSystem.domain.NationMember;
import kr.lunaf.nationSystem.domain.NationRole;
import kr.lunaf.nationSystem.domain.QuestKind;
import kr.lunaf.nationSystem.repository.DailyQuestRepository;
import kr.lunaf.nationSystem.repository.NationMemberRepository;
import kr.lunaf.nationSystem.repository.NationRepository;
import kr.lunaf.nationSystem.repository.TerritoryRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class QuestService {
    private final QuestsConfig questsConfig;
    private final DatabaseManager databaseManager;
    private final DailyQuestRepository dailyQuestRepository;
    private final NationMemberRepository memberRepository;
    private final NationRepository nationRepository;
    private final TerritoryRepository territoryRepository;
    private final NationService nationService;
    private final ExecutorService dbExecutor;
    private final Random random = new Random();

    public QuestService(
        QuestsConfig questsConfig,
        DatabaseManager databaseManager,
        DailyQuestRepository dailyQuestRepository,
        NationMemberRepository memberRepository,
        NationRepository nationRepository,
        TerritoryRepository territoryRepository,
        NationService nationService,
        ExecutorService dbExecutor
    ) {
        this.questsConfig = questsConfig;
        this.databaseManager = databaseManager;
        this.dailyQuestRepository = dailyQuestRepository;
        this.memberRepository = memberRepository;
        this.nationRepository = nationRepository;
        this.territoryRepository = territoryRepository;
        this.nationService = nationService;
        this.dbExecutor = dbExecutor;
    }

    public CompletableFuture<ServiceResult<List<DailyQuest>>> getOrCreateDailyQuests(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            if (!territoryRepository.hasBuiltWall(member.get().nationId())) {
                return ServiceResult.failure(Status.WALL_NOT_BUILT);
            }
            LocalDate today = LocalDate.now();
            List<DailyQuest> existing = dailyQuestRepository.listByNationAndDate(member.get().nationId(), today);
            if (!existing.isEmpty()) {
                return ServiceResult.success(existing);
            }
            if (member.get().role() != NationRole.OWNER) {
                return ServiceResult.failure(Status.OWNER_ONLY);
            }
            return ServiceResult.success(createDailyQuests(member.get().nationId()));
        }, dbExecutor);
    }

    public CompletableFuture<ServiceResult<List<DailyQuest>>> listDailyQuests(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            if (!territoryRepository.hasBuiltWall(member.get().nationId())) {
                return ServiceResult.failure(Status.WALL_NOT_BUILT);
            }
            LocalDate today = LocalDate.now();
            List<DailyQuest> existing = dailyQuestRepository.listByNationAndDate(member.get().nationId(), today);
            if (existing.isEmpty()) {
                return ServiceResult.failure(Status.NO_QUESTS);
            }
            return ServiceResult.success(existing);
        }, dbExecutor);
    }

    public CompletableFuture<ServiceResult<DailyQuest>> deliverItems(UUID playerUuid, int questId, int amount) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            LocalDate today = LocalDate.now();
            List<DailyQuest> quests = dailyQuestRepository.listByNationAndDate(member.get().nationId(), today);
            Optional<DailyQuest> quest = quests.stream().filter(q -> q.type().id() == questId).findFirst();
            if (quest.isEmpty()) {
                return ServiceResult.failure(Status.NO_QUESTS);
            }
            DailyQuest target = quest.get();
            if (target.completed()) {
                return ServiceResult.failure(Status.ALREADY_COMPLETED);
            }
            if (target.type().kind() != QuestKind.ITEM_DELIVERY) {
                return ServiceResult.failure(Status.INVALID_TYPE);
            }
            int newProgress = Math.min(target.requiredAmount(), target.progressAmount() + amount);
            boolean completed = newProgress >= target.requiredAmount();
            databaseManager.withTransaction(connection -> {
                dailyQuestRepository.updateProgress(connection, member.get().nationId(), questId, today, newProgress, completed);
                if (completed) {
                    int reward = randomReward();
                    nationRepository.addExp(connection, member.get().nationId(), reward);
                    nationService.sendNationMessage(member.get().nationId(),
                        "&6[Nation] &f일일 퀘스트 완료: &e" + target.type().displayName() + " &7(+EXP " + reward + ")");
                }
                return null;
            });
            return ServiceResult.success(new DailyQuest(
                target.id(),
                target.nationId(),
                target.type(),
                target.requiredAmount(),
                newProgress,
                completed,
                target.questDate()
            ));
        }, dbExecutor);
    }

    public void addProgress(long nationId, DailyQuestType type, int amount) {
        CompletableFuture.runAsync(() -> {
            LocalDate today = LocalDate.now();
            List<DailyQuest> quests = dailyQuestRepository.listByNationAndDate(nationId, today);
            for (DailyQuest quest : quests) {
                if (quest.type() != type || quest.completed()) {
                    continue;
                }
                int newProgress = Math.min(quest.requiredAmount(), quest.progressAmount() + amount);
                boolean completed = newProgress >= quest.requiredAmount();
                databaseManager.withTransaction(connection -> {
                    dailyQuestRepository.updateProgress(connection, nationId, quest.type().id(), today, newProgress, completed);
                    if (completed) {
                        int reward = randomReward();
                        nationRepository.addExp(connection, nationId, reward);
                        nationService.sendNationMessage(nationId,
                            "&6[Nation] &f일일 퀘스트 완료: &e" + quest.type().displayName() + " &7(+EXP " + reward + ")");
                    }
                    return null;
                });
                break;
            }
        }, dbExecutor);
    }

    private List<DailyQuest> createDailyQuests(long nationId) {
        LocalDate today = LocalDate.now();
        List<DailyQuestType> pool = new ArrayList<>(List.of(DailyQuestType.values()));
        int count = Math.min(questsConfig.dailyCount(), pool.size());
        int memberCount = nationRepository.countMembers(nationId);
        List<DailyQuest> quests = new ArrayList<>();
        databaseManager.withTransaction(connection -> {
            dailyQuestRepository.deleteByNationAndDate(connection, nationId, today);
            for (int i = 0; i < count; i++) {
                int index = random.nextInt(pool.size());
                DailyQuestType type = pool.remove(index);
                int base = type.baseAmount();
                int required = (int) Math.round(base * (1 + ((memberCount - 1) * 0.5)));
                DailyQuest quest = new DailyQuest(0L, nationId, type, required, 0, false, today);
                dailyQuestRepository.insertQuest(connection, quest);
                quests.add(quest);
            }
            return null;
        });
        return quests;
    }

    private int randomReward() {
        int min = questsConfig.rewardMinExp();
        int max = questsConfig.rewardMaxExp();
        return min + random.nextInt(Math.max(1, max - min + 1));
    }

    public enum Status {
        SUCCESS,
        NOT_IN_NATION,
        OWNER_ONLY,
        WALL_NOT_BUILT,
        NO_QUESTS,
        ALREADY_COMPLETED,
        INVALID_TYPE,
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
