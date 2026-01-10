package kr.lunaf.nationSystem.repository;

import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.Building;
import kr.lunaf.nationSystem.domain.BuildingState;
import kr.lunaf.nationSystem.domain.BuildingType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BuildingRepository {
    private final DatabaseManager databaseManager;

    public BuildingRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public long insertBuilding(Connection connection, Building building) throws Exception {
        String sql = "INSERT INTO nation_buildings " +
            "(territory_id, type, state, direction, world, base_x, base_y, base_z, level, build_complete_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, building.territoryId());
            statement.setString(2, building.type().name());
            statement.setString(3, building.state().name());
            statement.setString(4, building.direction());
            statement.setString(5, building.world());
            statement.setInt(6, building.baseX());
            statement.setInt(7, building.baseY());
            statement.setInt(8, building.baseZ());
            statement.setInt(9, building.level());
            if (building.buildCompleteAt() != null) {
                statement.setTimestamp(10, java.sql.Timestamp.from(building.buildCompleteAt()));
            } else {
                statement.setTimestamp(10, null);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new IllegalStateException("No generated key for building");
    }

    public List<Building> listByTerritory(long territoryId) {
        String sql = "SELECT * FROM nation_buildings WHERE territory_id = ?";
        List<Building> buildings = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, territoryId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    buildings.add(mapBuilding(rs));
                }
            }
            return buildings;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int countActiveByNationAndType(long nationId, BuildingType type) {
        String sql = "SELECT COUNT(*) FROM nation_buildings b " +
            "JOIN nation_territories t ON b.territory_id = t.id " +
            "WHERE t.nation_id = ? AND b.type = ? AND b.state = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.setString(2, type.name());
            statement.setString(3, BuildingState.ACTIVE.name());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int countPlacedByNationAndType(long nationId, BuildingType type) {
        String sql = "SELECT COUNT(*) FROM nation_buildings b " +
            "JOIN nation_territories t ON b.territory_id = t.id " +
            "WHERE t.nation_id = ? AND b.type = ? AND b.state <> ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.setString(2, type.name());
            statement.setString(3, BuildingState.DESTROYED.name());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<Building> listDueBuildings(Instant now) {
        String sql = "SELECT * FROM nation_buildings WHERE state = ? AND build_complete_at IS NOT NULL AND build_complete_at <= ?";
        List<Building> buildings = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, BuildingState.BUILDING.name());
            statement.setTimestamp(2, java.sql.Timestamp.from(now));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    buildings.add(mapBuilding(rs));
                }
            }
            return buildings;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void updateState(Connection connection, long buildingId, BuildingState state, Instant buildCompleteAt) throws Exception {
        String sql = "UPDATE nation_buildings SET state = ?, build_complete_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, state.name());
            if (buildCompleteAt != null) {
                statement.setTimestamp(2, java.sql.Timestamp.from(buildCompleteAt));
            } else {
                statement.setTimestamp(2, null);
            }
            statement.setLong(3, buildingId);
            statement.executeUpdate();
        }
    }

    private Building mapBuilding(ResultSet rs) throws Exception {
        java.sql.Timestamp buildTs = rs.getTimestamp("build_complete_at");
        Instant completeAt = buildTs != null ? buildTs.toInstant() : null;
        return new Building(
            rs.getLong("id"),
            rs.getLong("territory_id"),
            BuildingType.valueOf(rs.getString("type")),
            BuildingState.valueOf(rs.getString("state")),
            rs.getString("direction"),
            rs.getString("world"),
            rs.getInt("base_x"),
            rs.getInt("base_y"),
            rs.getInt("base_z"),
            rs.getInt("level"),
            completeAt
        );
    }
}
