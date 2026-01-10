package kr.lunaf.nationSystem.service;

import kr.lunaf.nationSystem.config.BuildingsConfig;
import kr.lunaf.nationSystem.domain.BuildingDefinition;
import kr.lunaf.nationSystem.domain.BuildingType;
import kr.lunaf.nationSystem.domain.NationMember;
import kr.lunaf.nationSystem.domain.NationRole;
import kr.lunaf.nationSystem.domain.Nation;
import kr.lunaf.nationSystem.repository.NationMemberRepository;
import kr.lunaf.nationSystem.repository.NationRepository;
import kr.lunaf.nationSystem.util.CustomItems;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class ShopService {
    private final BuildingsConfig buildingsConfig;
    private final NationMemberRepository memberRepository;
    private final NationRepository nationRepository;
    private final BuildingService buildingService;
    private final EconomyService economyService;
    private final CustomItems customItems;
    private final ExecutorService dbExecutor;
    private final Executor syncExecutor;

    public ShopService(
        BuildingsConfig buildingsConfig,
        NationMemberRepository memberRepository,
        NationRepository nationRepository,
        BuildingService buildingService,
        EconomyService economyService,
        CustomItems customItems,
        ExecutorService dbExecutor,
        Executor syncExecutor
    ) {
        this.buildingsConfig = buildingsConfig;
        this.memberRepository = memberRepository;
        this.nationRepository = nationRepository;
        this.buildingService = buildingService;
        this.economyService = economyService;
        this.customItems = customItems;
        this.dbExecutor = dbExecutor;
        this.syncExecutor = syncExecutor;
    }

    public CompletableFuture<ServiceResult<Void>> buyBuildingItem(Player player, BuildingType type) {
        return CompletableFuture.supplyAsync(() -> {
            UUID playerUuid = player.getUniqueId();
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            if (member.get().role() != NationRole.OWNER) {
                return ServiceResult.failure(Status.OWNER_ONLY);
            }
            BuildingDefinition definition = buildingsConfig.get(type);
            if (definition == null) {
                return ServiceResult.failure(Status.INVALID_TYPE);
            }
            Optional<Nation> nation = nationRepository.findById(member.get().nationId());
            if (nation.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            if (nation.get().level() < definition.minLevel()) {
                return ServiceResult.failure(Status.LEVEL_TOO_LOW);
            }
            boolean hasShop = buildingService.hasActiveBuilding(member.get().nationId(), BuildingType.SHOP).join();
            if (!hasShop) {
                return ServiceResult.failure(Status.NO_SHOP_BUILDING);
            }
            long price = definition.price();
            if (price > 0) {
                if (!economyService.isAvailable()) {
                    return ServiceResult.failure(Status.ECONOMY_UNAVAILABLE);
                }
                boolean withdrawn = economyService.withdrawSync(playerUuid, price, syncExecutor).join();
                if (!withdrawn) {
                    return ServiceResult.failure(Status.INSUFFICIENT_FUNDS);
                }
            }
            syncExecutor.execute(() -> player.getInventory().addItem(
                customItems.createBuildingItem(type, definition.displayName())
            ));
            return ServiceResult.success(null);
        }, dbExecutor);
    }

    public enum Status {
        SUCCESS,
        NOT_IN_NATION,
        OWNER_ONLY,
        INVALID_TYPE,
        LEVEL_TOO_LOW,
        NO_SHOP_BUILDING,
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
}
