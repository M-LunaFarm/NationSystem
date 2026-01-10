package kr.lunaf.nationSystem.service;

import kr.lunaf.nationSystem.config.PluginConfig;
import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.BuildingType;
import kr.lunaf.nationSystem.domain.NationMember;
import kr.lunaf.nationSystem.repository.NationMemberRepository;
import kr.lunaf.nationSystem.repository.NationRepository;
import kr.lunaf.nationSystem.repository.PresentClaimRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class PresentService {
    private final PluginConfig pluginConfig;
    private final DatabaseManager databaseManager;
    private final NationMemberRepository memberRepository;
    private final NationRepository nationRepository;
    private final PresentClaimRepository claimRepository;
    private final BuildingService buildingService;
    private final EconomyService economyService;
    private final ExecutorService dbExecutor;
    private final Executor syncExecutor;

    public PresentService(
        PluginConfig pluginConfig,
        DatabaseManager databaseManager,
        NationMemberRepository memberRepository,
        NationRepository nationRepository,
        PresentClaimRepository claimRepository,
        BuildingService buildingService,
        EconomyService economyService,
        ExecutorService dbExecutor,
        Executor syncExecutor
    ) {
        this.pluginConfig = pluginConfig;
        this.databaseManager = databaseManager;
        this.memberRepository = memberRepository;
        this.nationRepository = nationRepository;
        this.claimRepository = claimRepository;
        this.buildingService = buildingService;
        this.economyService = economyService;
        this.dbExecutor = dbExecutor;
        this.syncExecutor = syncExecutor;
    }

    public CompletableFuture<ServiceResult<PresentReward>> claim(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> memberRepository.findByPlayer(playerUuid), dbExecutor)
            .thenCompose(memberOpt -> {
                if (memberOpt.isEmpty()) {
                    return CompletableFuture.completedFuture(ServiceResult.failure(Status.NOT_IN_NATION));
                }
                NationMember member = memberOpt.get();
                return buildingService.hasActiveBuilding(member.nationId(), BuildingType.PRESENT)
                    .thenCompose(hasPresent -> {
                        if (!hasPresent) {
                            return CompletableFuture.completedFuture(ServiceResult.failure(Status.NO_PRESENT_BUILDING));
                        }
                        return CompletableFuture.supplyAsync(() -> claimInternal(member, playerUuid), dbExecutor);
                    });
            });
    }

    private ServiceResult<PresentReward> claimInternal(NationMember member, UUID playerUuid) {
        Instant now = Instant.now();
        Optional<Instant> lastClaim = claimRepository.getLastClaim(member.nationId());
        Duration cooldown = Duration.ofHours(pluginConfig.presentCooldownHours());
        if (lastClaim.isPresent()) {
            Duration since = Duration.between(lastClaim.get(), now);
            if (since.compareTo(cooldown) < 0) {
                long remaining = cooldown.minus(since).toSeconds();
                return ServiceResult.failure(Status.COOLDOWN, new PresentReward(0L, 0L, remaining));
            }
        }
        long rewardMoney = pluginConfig.presentRewardMoney();
        long rewardExp = pluginConfig.presentRewardExp();
        databaseManager.withTransaction(connection -> {
            claimRepository.upsertLastClaim(connection, member.nationId(), now);
            if (rewardExp > 0) {
                nationRepository.addExp(connection, member.nationId(), rewardExp);
            }
            return null;
        });
        if (rewardMoney > 0 && economyService.isAvailable()) {
            economyService.depositSync(playerUuid, rewardMoney, syncExecutor).join();
        }
        return ServiceResult.success(new PresentReward(rewardMoney, rewardExp, 0L));
    }

    public enum Status {
        SUCCESS,
        NOT_IN_NATION,
        NO_PRESENT_BUILDING,
        COOLDOWN,
        ERROR
    }

    public record PresentReward(long money, long exp, long remainingSeconds) {
    }

    public record ServiceResult<T>(Status status, T data) {
        public static <T> ServiceResult<T> success(T data) {
            return new ServiceResult<>(Status.SUCCESS, data);
        }

        public static <T> ServiceResult<T> failure(Status status) {
            return new ServiceResult<>(status, null);
        }

        public static <T> ServiceResult<T> failure(Status status, T data) {
            return new ServiceResult<>(status, data);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }
}
