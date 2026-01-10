package kr.lunaf.nationSystem.repository;

import kr.lunaf.nationSystem.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

public class NationStorageRepository {
    private final DatabaseManager databaseManager;

    public NationStorageRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<String> loadStorage(long nationId) {
        String sql = "SELECT contents FROM nation_storage WHERE nation_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("contents"));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void saveStorage(Connection connection, long nationId, String contents) throws Exception {
        String sql = "INSERT INTO nation_storage (nation_id, contents) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE contents = VALUES(contents), updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.setString(2, contents);
            statement.executeUpdate();
        }
    }
}
