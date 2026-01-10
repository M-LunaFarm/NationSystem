package kr.lunaf.nationSystem.service;

import kr.lunaf.nationSystem.config.PluginConfig;
import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.BuildingType;
import kr.lunaf.nationSystem.domain.NationMembership;
import kr.lunaf.nationSystem.repository.NationStorageRepository;
import kr.lunaf.nationSystem.util.ItemSerialization;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class StorageService {
    private final PluginConfig pluginConfig;
    private final DatabaseManager databaseManager;
    private final NationService nationService;
    private final BuildingService buildingService;
    private final NationStorageRepository storageRepository;
    private final ExecutorService dbExecutor;
    private final Executor syncExecutor;
    private final Map<Long, Inventory> storageCache = new ConcurrentHashMap<>();
    private final Map<Inventory, Long> inventoryToNation = new ConcurrentHashMap<>();

    public StorageService(
        PluginConfig pluginConfig,
        DatabaseManager databaseManager,
        NationService nationService,
        BuildingService buildingService,
        NationStorageRepository storageRepository,
        ExecutorService dbExecutor,
        Executor syncExecutor
    ) {
        this.pluginConfig = pluginConfig;
        this.databaseManager = databaseManager;
        this.nationService = nationService;
        this.buildingService = buildingService;
        this.storageRepository = storageRepository;
        this.dbExecutor = dbExecutor;
        this.syncExecutor = syncExecutor;
    }

    public CompletableFuture<ServiceResult<Inventory>> openStorage(UUID playerUuid) {
        return nationService.getMembership(playerUuid).thenCompose(result -> {
            if (!result.isSuccess()) {
                return CompletableFuture.completedFuture(ServiceResult.failure(Status.NOT_IN_NATION));
            }
            NationMembership membership = result.data();
            return buildingService.hasActiveBuilding(membership.nationId(), BuildingType.CHEST)
                .thenCompose(hasChest -> {
                    if (!hasChest) {
                        return CompletableFuture.completedFuture(ServiceResult.failure(Status.NO_STORAGE_BUILDING));
                    }
                    Inventory cached = storageCache.get(membership.nationId());
                    if (cached != null) {
                        return CompletableFuture.completedFuture(ServiceResult.success(cached));
                    }
                    return CompletableFuture.supplyAsync(() -> storageRepository.loadStorage(membership.nationId()), dbExecutor)
                        .thenCompose(data -> {
                            CompletableFuture<Inventory> created = new CompletableFuture<>();
                            syncExecutor.execute(() -> {
                                int size = normalizeSize(pluginConfig.storageSize());
                                Inventory inventory = Bukkit.createInventory(null, size, "Nation Storage");
                                ItemStack[] items = data.map(ItemSerialization::deserialize).orElse(new ItemStack[0]);
                                if (items.length > 0) {
                                    inventory.setContents(trimToSize(items, size));
                                }
                                storageCache.put(membership.nationId(), inventory);
                                inventoryToNation.put(inventory, membership.nationId());
                                created.complete(inventory);
                            });
                            return created.thenApply(ServiceResult::success);
                        });
                });
        });
    }

    public void handleClose(Inventory inventory) {
        Long nationId = inventoryToNation.get(inventory);
        if (nationId == null) {
            return;
        }
        if (!inventory.getViewers().isEmpty()) {
            return;
        }
        storageCache.remove(nationId);
        inventoryToNation.remove(inventory);
        String serialized = ItemSerialization.serialize(inventory.getContents());
        CompletableFuture.runAsync(() -> {
            databaseManager.withTransaction(connection -> {
                storageRepository.saveStorage(connection, nationId, serialized);
                return null;
            });
        }, dbExecutor);
    }

    private int normalizeSize(int size) {
        int clamped = Math.min(54, Math.max(9, size));
        return (clamped / 9) * 9;
    }

    private ItemStack[] trimToSize(ItemStack[] items, int size) {
        ItemStack[] trimmed = new ItemStack[size];
        int length = Math.min(items.length, size);
        System.arraycopy(items, 0, trimmed, 0, length);
        return trimmed;
    }

    public enum Status {
        SUCCESS,
        NOT_IN_NATION,
        NO_STORAGE_BUILDING,
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
