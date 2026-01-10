package kr.lunaf.nationSystem.repository;

import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.NationTerritory;
import kr.lunaf.nationSystem.domain.WallStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TerritoryRepository {
    private final DatabaseManager databaseManager;

    public TerritoryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public long insertTerritory(Connection connection, NationTerritory territory) throws Exception {
        String sql = "INSERT INTO nation_territories " +
            "(nation_id, world, center_x, center_y, center_z, size, wall_status, wall_expires_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, territory.nationId());
            statement.setString(2, territory.world());
            statement.setInt(3, territory.centerX());
            statement.setInt(4, territory.centerY());
            statement.setInt(5, territory.centerZ());
            statement.setInt(6, territory.size());
            statement.setString(7, territory.wallStatus().name());
            if (territory.wallExpiresAt() != null) {
                statement.setTimestamp(8, java.sql.Timestamp.from(territory.wallExpiresAt()));
            } else {
                statement.setTimestamp(8, null);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new IllegalStateException("No generated key for territory");
    }

    public List<NationTerritory> listByNation(long nationId) {
        String sql = "SELECT * FROM nation_territories WHERE nation_id = ? ORDER BY id ASC";
        List<NationTerritory> territories = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    territories.add(mapTerritory(rs));
                }
            }
            return territories;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<NationTerritory> listAll() {
        String sql = "SELECT * FROM nation_territories";
        List<NationTerritory> territories = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                territories.add(mapTerritory(rs));
            }
            return territories;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int countByNation(long nationId) {
        String sql = "SELECT COUNT(*) FROM nation_territories WHERE nation_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
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

    public int countByNation(Connection connection, long nationId) throws Exception {
        String sql = "SELECT COUNT(*) FROM nation_territories WHERE nation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    public boolean hasBuiltWall(long nationId) {
        String sql = "SELECT 1 FROM nation_territories WHERE nation_id = ? AND wall_status = ? LIMIT 1";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.setString(2, WallStatus.BUILT.name());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<NationTerritory> findById(long territoryId) {
        String sql = "SELECT * FROM nation_territories WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, territoryId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTerritory(rs));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<NationTerritory> listPendingExpired(Instant now) {
        String sql = "SELECT * FROM nation_territories WHERE wall_status = ? AND wall_expires_at IS NOT NULL AND wall_expires_at < ?";
        List<NationTerritory> territories = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, WallStatus.PENDING.name());
            statement.setTimestamp(2, java.sql.Timestamp.from(now));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    territories.add(mapTerritory(rs));
                }
            }
            return territories;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void updateWallStatus(Connection connection, long territoryId, WallStatus status, Instant expiresAt) throws Exception {
        String sql = "UPDATE nation_territories SET wall_status = ?, wall_expires_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            if (expiresAt != null) {
                statement.setTimestamp(2, java.sql.Timestamp.from(expiresAt));
            } else {
                statement.setTimestamp(2, null);
            }
            statement.setLong(3, territoryId);
            statement.executeUpdate();
        }
    }

    public void deleteTerritory(Connection connection, long territoryId) throws Exception {
        String sql = "DELETE FROM nation_territories WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, territoryId);
            statement.executeUpdate();
        }
    }

    public void deleteByNation(Connection connection, long nationId) throws Exception {
        String sql = "DELETE FROM nation_territories WHERE nation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.executeUpdate();
        }
    }

    private NationTerritory mapTerritory(ResultSet rs) throws Exception {
        java.sql.Timestamp expires = rs.getTimestamp("wall_expires_at");
        Instant expiresAt = expires != null ? expires.toInstant() : null;
        return new NationTerritory(
            rs.getLong("id"),
            rs.getLong("nation_id"),
            rs.getString("world"),
            rs.getInt("center_x"),
            rs.getInt("center_y"),
            rs.getInt("center_z"),
            rs.getInt("size"),
            WallStatus.valueOf(rs.getString("wall_status")),
            expiresAt
        );
    }
}
