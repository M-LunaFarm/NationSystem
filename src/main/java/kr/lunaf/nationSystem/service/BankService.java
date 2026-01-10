package kr.lunaf.nationSystem.service;

import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.BankHistoryEntry;
import kr.lunaf.nationSystem.domain.BankHistoryType;
import kr.lunaf.nationSystem.domain.NationMember;
import kr.lunaf.nationSystem.domain.BuildingType;
import kr.lunaf.nationSystem.repository.BankHistoryRepository;
import kr.lunaf.nationSystem.repository.NationMemberRepository;
import kr.lunaf.nationSystem.repository.NationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class BankService {
    private final DatabaseManager databaseManager;
    private final NationRepository nationRepository;
    private final NationMemberRepository memberRepository;
    private final BankHistoryRepository historyRepository;
    private final BuildingService buildingService;
    private final EconomyService economyService;
    private final ExecutorService dbExecutor;
    private final Executor syncExecutor;

    public BankService(
        DatabaseManager databaseManager,
        NationRepository nationRepository,
        NationMemberRepository memberRepository,
        BankHistoryRepository historyRepository,
        BuildingService buildingService,
        EconomyService economyService,
        ExecutorService dbExecutor,
        Executor syncExecutor
    ) {
        this.databaseManager = databaseManager;
        this.nationRepository = nationRepository;
        this.memberRepository = memberRepository;
        this.historyRepository = historyRepository;
        this.buildingService = buildingService;
        this.economyService = economyService;
        this.dbExecutor = dbExecutor;
        this.syncExecutor = syncExecutor;
    }

    public CompletableFuture<ServiceResult<Long>> getBalance(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return PendingBalance.failure(Status.NOT_IN_NATION);
            }
            return PendingBalance.success(member.get());
        }, dbExecutor).thenCompose(result -> {
            if (!result.isSuccess()) {
                return CompletableFuture.completedFuture(ServiceResult.failure(result.status()));
            }
            NationMember member = result.data();
            return buildingService.hasActiveBuilding(member.nationId(), BuildingType.BANK)
                .thenCompose(hasBank -> {
                    if (!hasBank) {
                        return CompletableFuture.completedFuture(ServiceResult.failure(Status.NO_BANK_BUILDING));
                    }
                    return CompletableFuture.supplyAsync(() -> nationRepository.findById(member.nationId())
                        .map(nation -> ServiceResult.success(nation.bankBalance()))
                        .orElse(ServiceResult.failure(Status.NOT_IN_NATION)), dbExecutor);
                });
        });
    }

    public CompletableFuture<ServiceResult<List<BankHistoryEntry>>> getHistory(UUID playerUuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return PendingBalance.failure(Status.NOT_IN_NATION);
            }
            return PendingBalance.success(member.get());
        }, dbExecutor).thenCompose(result -> {
            if (!result.isSuccess()) {
                return CompletableFuture.completedFuture(ServiceResult.failure(result.status()));
            }
            NationMember member = result.data();
            return buildingService.hasActiveBuilding(member.nationId(), BuildingType.BANK)
                .thenCompose(hasBank -> {
                    if (!hasBank) {
                        return CompletableFuture.completedFuture(ServiceResult.failure(Status.NO_BANK_BUILDING));
                    }
                    return CompletableFuture.supplyAsync(() -> {
                        List<BankHistoryEntry> entries = historyRepository.listRecent(member.nationId(), limit);
                        return ServiceResult.success(entries);
                    }, dbExecutor);
                });
        });
    }

    public CompletableFuture<ServiceResult<Long>> deposit(UUID playerUuid, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            if (!economyService.isAvailable()) {
                return PendingDepositResult.failure(Status.ECONOMY_UNAVAILABLE);
            }
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return PendingDepositResult.failure(Status.NOT_IN_NATION);
            }
            return PendingDepositResult.success(new PendingDeposit(member.get().nationId(), member.get().playerUuid()));
        }, dbExecutor).thenCompose(result -> {
            if (!result.isSuccess()) {
                return CompletableFuture.completedFuture(ServiceResult.failure(result.status()));
            }
            PendingDeposit pending = result.data();
            return buildingService.hasActiveBuilding(pending.nationId(), BuildingType.BANK)
                .thenCompose(hasBank -> {
                    if (!hasBank) {
                        return CompletableFuture.completedFuture(ServiceResult.failure(Status.NO_BANK_BUILDING));
                    }
                    return economyService.withdrawSync(playerUuid, amount, syncExecutor)
                        .thenCompose(withdrawn -> {
                            if (!withdrawn) {
                                return CompletableFuture.completedFuture(ServiceResult.failure(Status.INSUFFICIENT_FUNDS));
                            }
                            return CompletableFuture.supplyAsync(() -> {
                                long newBalance = databaseManager.withTransaction(connection -> {
                                    long current = nationRepository.getBankBalanceForUpdate(connection, pending.nationId());
                                    long updated = current + amount;
                                    nationRepository.addBankBalance(connection, pending.nationId(), amount);
                                    historyRepository.insert(connection, pending.nationId(), BankHistoryType.DEPOSIT, amount, pending.playerUuid());
                                    return updated;
                                });
                                return ServiceResult.success(newBalance);
                            }, dbExecutor);
                        });
                });
        });
    }

    public enum Status {
        SUCCESS,
        NOT_IN_NATION,
        NO_BANK_BUILDING,
        ECONOMY_UNAVAILABLE,
        INSUFFICIENT_FUNDS,
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

    private record PendingDeposit(long nationId, UUID playerUuid) {
    }

    private record PendingBalance(Status status, NationMember data) {
        public static PendingBalance success(NationMember data) {
            return new PendingBalance(Status.SUCCESS, data);
        }

        public static PendingBalance failure(Status status) {
            return new PendingBalance(status, null);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }

    private record PendingDepositResult(Status status, PendingDeposit data) {
        public static PendingDepositResult success(PendingDeposit data) {
            return new PendingDepositResult(Status.SUCCESS, data);
        }

        public static PendingDepositResult failure(Status status) {
            return new PendingDepositResult(status, null);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }
}
