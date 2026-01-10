package kr.lunaf.nationSystem.service;

import kr.lunaf.nationSystem.config.PluginConfig;
import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.Nation;
import kr.lunaf.nationSystem.domain.NationMember;
import kr.lunaf.nationSystem.domain.NationRole;
import kr.lunaf.nationSystem.domain.NationTerritory;
import kr.lunaf.nationSystem.domain.WallStatus;
import kr.lunaf.nationSystem.repository.NationMemberRepository;
import kr.lunaf.nationSystem.repository.NationRepository;
import kr.lunaf.nationSystem.repository.NationSettingsRepository;
import kr.lunaf.nationSystem.repository.TerritoryRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class TerritoryService {
    private final PluginConfig pluginConfig;
    private final DatabaseManager databaseManager;
    private final NationRepository nationRepository;
    private final NationMemberRepository memberRepository;
    private final NationSettingsRepository settingsRepository;
    private final TerritoryRepository territoryRepository;
    private final StructureService structureService;
    private final NationService nationService;
    private final ExecutorService dbExecutor;
    private final Executor syncExecutor;

    public TerritoryService(
        PluginConfig pluginConfig,
        DatabaseManager databaseManager,
        NationRepository nationRepository,
        NationMemberRepository memberRepository,
        NationSettingsRepository settingsRepository,
        TerritoryRepository territoryRepository,
        StructureService structureService,
        NationService nationService,
        ExecutorService dbExecutor,
        Executor syncExecutor
    ) {
        this.pluginConfig = pluginConfig;
        this.databaseManager = databaseManager;
        this.nationRepository = nationRepository;
        this.memberRepository = memberRepository;
        this.settingsRepository = settingsRepository;
        this.territoryRepository = territoryRepository;
        this.structureService = structureService;
        this.nationService = nationService;
        this.dbExecutor = dbExecutor;
        this.syncExecutor = syncExecutor;
    }

    public boolean hasPlayersInArea(World world, Location center, UUID ignore) {
        BlockArea area = BlockArea.fromCenter(center, pluginConfig.territorySize());
        for (Player player : world.getPlayers()) {
            if (player.getUniqueId().equals(ignore)) {
                continue;
            }
            Location loc = player.getLocation();
            if (area.contains(loc.getBlockX(), loc.getBlockZ())) {
                return true;
            }
        }
        return false;
    }

    public CompletableFuture<ServiceResult<TerritoryResult>> createTerritory(UUID playerUuid, Location center, String nationName) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            boolean hasNation = member.isPresent();
            long nationId;
            String finalNationName;
            boolean createdNation = false;
            int nationLevel = 1;
            if (hasNation) {
                if (member.get().role() != NationRole.OWNER) {
                    return ServiceResult.failure(Status.NOT_OWNER);
                }
                nationId = member.get().nationId();
                Optional<Nation> nation = nationRepository.findById(nationId);
                if (nation.isEmpty()) {
                    return ServiceResult.failure(Status.NOT_IN_NATION);
                }
                finalNationName = nation.get().name();
                nationLevel = nation.get().level();
            } else {
                if (nationName == null || nationName.isBlank()) {
                    return ServiceResult.failure(Status.NAME_REQUIRED);
                }
                if (nationRepository.findByName(nationName).isPresent()) {
                    return ServiceResult.failure(Status.NAME_TAKEN);
                }
                finalNationName = nationName;
                createdNation = true;
                nationId = databaseManager.withTransaction(connection -> {
                    long createdId = nationRepository.insertNation(
                        connection,
                        new Nation(0L, finalNationName, playerUuid, 1, 0L, 0L, 0)
                    );
                    settingsRepository.insertDefaults(connection, createdId);
                    memberRepository.insertMember(connection, createdId, playerUuid, NationRole.OWNER);
                    return createdId;
                });
                nationService.clearMembership(playerUuid);
            }

            int territoryCount = territoryRepository.countByNation(nationId);
            if (territoryCount >= pluginConfig.maxTerritoriesForLevel(nationLevel)) {
                return ServiceResult.failure(Status.TOO_MANY_TERRITORIES);
            }

            List<NationTerritory> territories = territoryRepository.listAll();
            int minDistance = pluginConfig.territoryMinDistance();
            for (NationTerritory territory : territories) {
                if (!territory.world().equalsIgnoreCase(center.getWorld().getName())) {
                    continue;
                }
                double distance = center.distance(new Location(
                    center.getWorld(),
                    territory.centerX(),
                    territory.centerY(),
                    territory.centerZ()
                ));
                if (distance < minDistance) {
                    return ServiceResult.failure(Status.TOO_CLOSE);
                }
            }

            Instant expiresAt = Instant.now().plus(Duration.ofMinutes(pluginConfig.wallExpireMinutes()));
            NationTerritory territory = new NationTerritory(
                0L,
                nationId,
                center.getWorld().getName(),
                center.getBlockX(),
                center.getBlockY(),
                center.getBlockZ(),
                pluginConfig.territorySize(),
                WallStatus.PENDING,
                expiresAt
            );
            long territoryId = databaseManager.withTransaction(connection -> territoryRepository.insertTerritory(connection, territory));
            return ServiceResult.success(new TerritoryResult(territoryId, nationId, finalNationName, createdNation));
        }, dbExecutor);
    }

    public CompletableFuture<ServiceResult<Void>> buildWall(UUID playerUuid, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            if (member.get().role() != NationRole.OWNER) {
                return ServiceResult.failure(Status.NOT_OWNER);
            }
            List<NationTerritory> territories = territoryRepository.listByNation(member.get().nationId());
            Optional<NationTerritory> territory = territories.stream()
                .filter(value -> isInsideTerritory(location, value))
                .findFirst();
            if (territory.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_TERRITORY);
            }
            if (territory.get().wallStatus() != WallStatus.PENDING) {
                return ServiceResult.failure(Status.NO_PENDING_WALL);
            }
            CompletableFuture<Boolean> placedFuture = new CompletableFuture<>();
            syncExecutor.execute(() -> placedFuture.complete(buildWallBlocks(location.getWorld(), territory.get())));
            boolean placed = placedFuture.join();
            if (!placed) {
                return ServiceResult.failure(Status.STRUCTURE_MISSING);
            }
            databaseManager.withTransaction(connection -> {
                territoryRepository.updateWallStatus(connection, territory.get().id(), WallStatus.BUILT, null);
                return null;
            });
            return ServiceResult.success(null);
        }, dbExecutor);
    }

    public CompletableFuture<ServiceResult<List<NationTerritory>>> listTerritories(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            List<NationTerritory> territories = territoryRepository.listByNation(member.get().nationId());
            return ServiceResult.success(territories);
        }, dbExecutor);
    }

    private boolean buildWallBlocks(World world, NationTerritory territory) {
        if (!structureService.hasWallStructure() || !structureService.hasCenterStructure()) {
            return false;
        }
        Location center = new Location(world, territory.centerX(), territory.centerY(), territory.centerZ());
        int size = territory.size();
        int minY = Math.max(world.getMinHeight(), 0);
        int maxY = Math.min(world.getMaxHeight() - 1, 256);

        BlockArea outer = BlockArea.fromCenter(center, size);
        BlockArea inner = BlockArea.fromCenter(center, size - 8);
        BlockArea grassOuter = BlockArea.fromCenter(center, size - 2);
        BlockArea grassInner = BlockArea.fromCenter(center, size - 6);
        BlockArea clearInner = BlockArea.fromCenter(center, size - 10);

        fillRing(world, outer, inner, minY, maxY, Material.BEDROCK);
        fillRing(world, grassOuter, grassInner, minY, minY, Material.GRASS_BLOCK);
        clearColumn(world, center, 2, minY, maxY);
        clearArea(world, clearInner, minY + 1, maxY);
        fillArea(world, clearInner, minY, minY, Material.GRASS_BLOCK);

        Location paste = center.clone().add(0, 1, 0);
        boolean wallPlaced = structureService.placeWallStructure(paste);
        boolean centerPlaced = structureService.placeCenterStructure(paste);
        return wallPlaced && centerPlaced;
    }

    private void clearColumn(World world, Location center, int radius, int minY, int maxY) {
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = Math.max(minY, center.getBlockY() + 1); y <= maxY; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private void clearArea(World world, BlockArea area, int minY, int maxY) {
        fillArea(world, area, minY, maxY, Material.AIR);
    }

    private void fillArea(World world, BlockArea area, int minY, int maxY, Material material) {
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private void fillRing(World world, BlockArea outer, BlockArea inner, int minY, int maxY, Material material) {
        for (int x = outer.minX(); x <= outer.maxX(); x++) {
            for (int z = outer.minZ(); z <= outer.maxZ(); z++) {
                boolean insideInner = x >= inner.minX() && x <= inner.maxX() && z >= inner.minZ() && z <= inner.maxZ();
                if (insideInner) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private boolean isInsideTerritory(Location location, NationTerritory territory) {
        if (!location.getWorld().getName().equalsIgnoreCase(territory.world())) {
            return false;
        }
        Location center = new Location(location.getWorld(), territory.centerX(), territory.centerY(), territory.centerZ());
        BlockArea area = BlockArea.fromCenter(center, territory.size());
        return area.contains(location.getBlockX(), location.getBlockZ());
    }

    public CompletableFuture<Void> expirePendingTerritories() {
        return CompletableFuture.runAsync(() -> {
            List<NationTerritory> expired = territoryRepository.listPendingExpired(Instant.now());
            for (NationTerritory territory : expired) {
                boolean deletedNation = databaseManager.withTransaction(connection -> {
                    territoryRepository.deleteTerritory(connection, territory.id());
                    int remaining = territoryRepository.countByNation(connection, territory.nationId());
                    if (remaining == 0) {
                        memberRepository.deleteByNation(connection, territory.nationId());
                        settingsRepository.deleteByNation(connection, territory.nationId());
                        nationRepository.deleteNation(connection, territory.nationId());
                        return true;
                    }
                    return false;
                });
                if (deletedNation) {
                    nationService.clearMembershipsForNation(territory.nationId());
                }
            }
        }, dbExecutor);
    }

    public enum Status {
        SUCCESS,
        NAME_REQUIRED,
        NAME_TAKEN,
        NOT_OWNER,
        NOT_IN_NATION,
        TOO_MANY_TERRITORIES,
        TOO_CLOSE,
        NOT_IN_TERRITORY,
        NO_PENDING_WALL,
        STRUCTURE_MISSING,
        ERROR
    }

    public record TerritoryResult(long territoryId, long nationId, String nationName, boolean createdNation) {
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

    public record BlockArea(int minX, int maxX, int minZ, int maxZ) {
        public static BlockArea fromCenter(Location center, int size) {
            int c = ((size - 1) / 2) + 1;
            int minX = center.getBlockX() - c;
            int maxX = center.getBlockX() + c;
            int minZ = center.getBlockZ() - c;
            int maxZ = center.getBlockZ() + c;
            return new BlockArea(minX, maxX, minZ, maxZ);
        }

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }
}
