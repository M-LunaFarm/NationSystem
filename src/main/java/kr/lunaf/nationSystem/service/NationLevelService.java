package kr.lunaf.nationSystem.service;

import kr.lunaf.nationSystem.config.PluginConfig;
import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.BankHistoryType;
import kr.lunaf.nationSystem.domain.Nation;
import kr.lunaf.nationSystem.domain.NationMember;
import kr.lunaf.nationSystem.domain.NationRole;
import kr.lunaf.nationSystem.repository.BankHistoryRepository;
import kr.lunaf.nationSystem.repository.NationMemberRepository;
import kr.lunaf.nationSystem.repository.NationRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class NationLevelService {
    private final PluginConfig pluginConfig;
    private final DatabaseManager databaseManager;
    private final NationRepository nationRepository;
    private final NationMemberRepository memberRepository;
    private final BankHistoryRepository historyRepository;
    private final NationService nationService;
    private final ExecutorService dbExecutor;

    public NationLevelService(
        PluginConfig pluginConfig,
        DatabaseManager databaseManager,
        NationRepository nationRepository,
        NationMemberRepository memberRepository,
        BankHistoryRepository historyRepository,
        NationService nationService,
        ExecutorService dbExecutor
    ) {
        this.pluginConfig = pluginConfig;
        this.databaseManager = databaseManager;
        this.nationRepository = nationRepository;
        this.memberRepository = memberRepository;
        this.historyRepository = historyRepository;
        this.nationService = nationService;
        this.dbExecutor = dbExecutor;
    }

    public CompletableFuture<ServiceResult<LevelInfo>> getLevelInfo(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            Optional<Nation> nation = nationRepository.findById(member.get().nationId());
            if (nation.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            int currentLevel = nation.get().level();
            long expCost = pluginConfig.levelUpExpCost(currentLevel);
            long moneyCost = pluginConfig.levelUpMoneyCost(currentLevel);
            return ServiceResult.success(new LevelInfo(
                currentLevel,
                nation.get().exp(),
                nation.get().bankBalance(),
                expCost,
                moneyCost,
                pluginConfig.maxLevel()
            ));
        }, dbExecutor);
    }

    public CompletableFuture<ServiceResult<LevelInfo>> levelUp(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            if (member.get().role() != NationRole.OWNER) {
                return ServiceResult.failure(Status.OWNER_ONLY);
            }
            Optional<Nation> nation = nationRepository.findById(member.get().nationId());
            if (nation.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            int currentLevel = nation.get().level();
            long expCost = pluginConfig.levelUpExpCost(currentLevel);
            long moneyCost = pluginConfig.levelUpMoneyCost(currentLevel);
            if (expCost < 0 || moneyCost < 0) {
                return ServiceResult.failure(Status.MAX_LEVEL);
            }
            if (nation.get().exp() < expCost) {
                return ServiceResult.failure(Status.NOT_ENOUGH_EXP);
            }
            if (nation.get().bankBalance() < moneyCost) {
                return ServiceResult.failure(Status.NOT_ENOUGH_MONEY);
            }
            databaseManager.withTransaction(connection -> {
                nationRepository.levelUpNation(connection, nation.get().id(), expCost, moneyCost);
                historyRepository.insert(connection, nation.get().id(), BankHistoryType.LEVEL_UP, moneyCost, member.get().playerUuid());
                return null;
            });
            nationService.clearMembershipsForNation(nation.get().id());
            return ServiceResult.success(new LevelInfo(
                currentLevel + 1,
                nation.get().exp() - expCost,
                nation.get().bankBalance() - moneyCost,
                pluginConfig.levelUpExpCost(currentLevel + 1),
                pluginConfig.levelUpMoneyCost(currentLevel + 1),
                pluginConfig.maxLevel()
            ));
        }, dbExecutor);
    }

    public enum Status {
        SUCCESS,
        NOT_IN_NATION,
        OWNER_ONLY,
        NOT_ENOUGH_EXP,
        NOT_ENOUGH_MONEY,
        MAX_LEVEL,
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

    public record LevelInfo(
        int level,
        long exp,
        long bankBalance,
        long nextExpCost,
        long nextMoneyCost,
        int maxLevel
    ) {
    }
}
