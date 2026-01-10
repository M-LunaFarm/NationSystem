package kr.lunaf.nationSystem.service;

import kr.lunaf.nationSystem.config.BuildingsConfig;
import kr.lunaf.nationSystem.config.PluginConfig;
import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.Building;
import kr.lunaf.nationSystem.domain.BuildingDefinition;
import kr.lunaf.nationSystem.domain.BuildingState;
import kr.lunaf.nationSystem.domain.BuildingType;
import kr.lunaf.nationSystem.domain.NationMember;
import kr.lunaf.nationSystem.domain.NationRole;
import kr.lunaf.nationSystem.domain.Nation;
import kr.lunaf.nationSystem.domain.NationTerritory;
import kr.lunaf.nationSystem.domain.WallStatus;
import kr.lunaf.nationSystem.repository.BuildingRepository;
import kr.lunaf.nationSystem.repository.NationMemberRepository;
import kr.lunaf.nationSystem.repository.NationRepository;
import kr.lunaf.nationSystem.repository.TerritoryRepository;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class BuildingService {
    private final PluginConfig pluginConfig;
    private final BuildingsConfig buildingsConfig;
    private final DatabaseManager databaseManager;
    private final BuildingRepository buildingRepository;
    private final NationRepository nationRepository;
    private final TerritoryRepository territoryRepository;
    private final NationMemberRepository memberRepository;
    private final StructureService structureService;
    private final ExecutorService dbExecutor;
    private final Executor syncExecutor;

    public BuildingService(
        PluginConfig pluginConfig,
        BuildingsConfig buildingsConfig,
        DatabaseManager databaseManager,
        BuildingRepository buildingRepository,
        NationRepository nationRepository,
        TerritoryRepository territoryRepository,
        NationMemberRepository memberRepository,
        StructureService structureService,
        ExecutorService dbExecutor,
        Executor syncExecutor
    ) {
        this.pluginConfig = pluginConfig;
        this.buildingsConfig = buildingsConfig;
        this.databaseManager = databaseManager;
        this.buildingRepository = buildingRepository;
        this.nationRepository = nationRepository;
        this.territoryRepository = territoryRepository;
        this.memberRepository = memberRepository;
        this.structureService = structureService;
        this.dbExecutor = dbExecutor;
        this.syncExecutor = syncExecutor;
    }

    public CompletableFuture<ServiceResult<Building>> placeBuilding(
        UUID playerUuid,
        Location baseLocation,
        BuildingType type,
        BlockFace facing
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            if (member.get().role() != NationRole.OWNER) {
                return ServiceResult.failure(Status.NOT_OWNER);
            }
            Optional<Nation> nation = nationRepository.findById(member.get().nationId());
            if (nation.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            Optional<NationTerritory> territory = findTerritoryForNation(member.get().nationId(), baseLocation);
            if (territory.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_TERRITORY);
            }
            if (territory.get().wallStatus() != WallStatus.BUILT) {
                return ServiceResult.failure(Status.WALL_NOT_BUILT);
            }
            if (baseLocation.getBlockY() != territory.get().centerY()) {
                return ServiceResult.failure(Status.INVALID_Y);
            }
            if (isTooCloseToExisting(territory.get().id(), baseLocation)) {
                return ServiceResult.failure(Status.TOO_CLOSE);
            }
            BuildingDefinition definition = buildingsConfig.get(type);
            if (definition == null || definition.structurePath().isBlank()) {
                return ServiceResult.failure(Status.INVALID_TYPE);
            }
            if (nation.get().level() < definition.minLevel()) {
                return ServiceResult.failure(Status.LEVEL_TOO_LOW);
            }
            int placed = buildingRepository.countPlacedByNationAndType(member.get().nationId(), type);
            if (placed >= definition.maxPerNation()) {
                return ServiceResult.failure(Status.LIMIT_REACHED);
            }
            if (!structureService.hasBuildingStructure(definition.structurePath())) {
                return ServiceResult.failure(Status.STRUCTURE_MISSING);
            }
            String direction = normalizeDirection(facing);
            Instant completeAt = Instant.now().plusSeconds(definition.buildTimeSeconds());
            Building building = new Building(
                0L,
                territory.get().id(),
                type,
                BuildingState.BUILDING,
                direction,
                baseLocation.getWorld().getName(),
                baseLocation.getBlockX(),
                baseLocation.getBlockY(),
                baseLocation.getBlockZ(),
                1,
                completeAt
            );
            long id = databaseManager.withTransaction(connection -> buildingRepository.insertBuilding(connection, building));
            Building stored = new Building(
                id,
                building.territoryId(),
                building.type(),
                building.state(),
                building.direction(),
                building.world(),
                building.baseX(),
                building.baseY(),
                building.baseZ(),
                building.level(),
                building.buildCompleteAt()
            );
            return ServiceResult.success(stored);
        }, dbExecutor);
    }

    public CompletableFuture<Void> processBuildingCompletion() {
        return CompletableFuture.runAsync(() -> {
            List<Building> due = buildingRepository.listDueBuildings(Instant.now());
            for (Building building : due) {
                BuildingDefinition definition = buildingsConfig.get(building.type());
                if (definition == null || !structureService.hasBuildingStructure(definition.structurePath())) {
                    continue;
                }
                databaseManager.withTransaction(connection -> {
                    buildingRepository.updateState(connection, building.id(), BuildingState.ACTIVE, null);
                    return null;
                });
                schedulePlacement(building, definition);
            }
        }, dbExecutor);
    }

    public CompletableFuture<Boolean> hasActiveBuilding(long nationId, BuildingType type) {
        return CompletableFuture.supplyAsync(() -> {
            return buildingRepository.countActiveByNationAndType(nationId, type) > 0;
        }, dbExecutor);
    }

    private void schedulePlacement(Building building, BuildingDefinition definition) {
        syncExecutor.execute(() -> {
            Location location = new Location(
                org.bukkit.Bukkit.getWorld(building.world()),
                building.baseX(),
                building.baseY(),
                building.baseZ()
            );
            if (location.getWorld() == null) {
                return;
            }
            structureService.placeBuildingStructure(definition.structurePath(), location, building.direction());
        });
    }

    private Optional<NationTerritory> findTerritoryForNation(long nationId, Location location) {
        List<NationTerritory> territories = territoryRepository.listByNation(nationId);
        for (NationTerritory territory : territories) {
            if (!territory.world().equalsIgnoreCase(location.getWorld().getName())) {
                continue;
            }
            int size = territory.size();
            int c = ((size - 1) / 2) + 1;
            int minX = territory.centerX() - c;
            int maxX = territory.centerX() + c;
            int minZ = territory.centerZ() - c;
            int maxZ = territory.centerZ() + c;
            int x = location.getBlockX();
            int z = location.getBlockZ();
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                return Optional.of(territory);
            }
        }
        return Optional.empty();
    }

    private boolean isTooCloseToExisting(long territoryId, Location baseLocation) {
        int spacing = pluginConfig.buildingMinSpacing();
        List<Building> buildings = buildingRepository.listByTerritory(territoryId);
        for (Building building : buildings) {
            if (building.state() == BuildingState.DESTROYED) {
                continue;
            }
            int dx = Math.abs(building.baseX() - baseLocation.getBlockX());
            int dz = Math.abs(building.baseZ() - baseLocation.getBlockZ());
            if (dx <= spacing && dz <= spacing) {
                return true;
            }
        }
        return false;
    }

    private String normalizeDirection(BlockFace face) {
        if (face == null) {
            return "SOUTH";
        }
        return switch (face) {
            case WEST -> "WEST";
            case NORTH -> "NORTH";
            case EAST -> "EAST";
            default -> "SOUTH";
        };
    }

    public enum Status {
        SUCCESS,
        NOT_IN_NATION,
        NOT_OWNER,
        NOT_IN_TERRITORY,
        WALL_NOT_BUILT,
        INVALID_Y,
        TOO_CLOSE,
        LEVEL_TOO_LOW,
        LIMIT_REACHED,
        INVALID_TYPE,
        STRUCTURE_MISSING,
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
