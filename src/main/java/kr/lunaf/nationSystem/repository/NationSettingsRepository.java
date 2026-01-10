package kr.lunaf.nationSystem.repository;

import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.NationSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

public class NationSettingsRepository {
    private final DatabaseManager databaseManager;

    public NationSettingsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void insertDefaults(Connection connection, long nationId) throws Exception {
        String sql = "INSERT INTO nation_settings (nation_id, pvp_enabled, invite_lock, chat_default) VALUES (?, 0, 0, 0)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.executeUpdate();
        }
    }

    public Optional<NationSettings> findByNationId(long nationId) {
        String sql = "SELECT nation_id, pvp_enabled, invite_lock, chat_default FROM nation_settings WHERE nation_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new NationSettings(
                        rs.getLong("nation_id"),
                        rs.getInt("pvp_enabled") == 1,
                        rs.getInt("invite_lock") == 1,
                        rs.getInt("chat_default") == 1
                    ));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteByNation(Connection connection, long nationId) throws Exception {
        String sql = "DELETE FROM nation_settings WHERE nation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.executeUpdate();
        }
    }
}
