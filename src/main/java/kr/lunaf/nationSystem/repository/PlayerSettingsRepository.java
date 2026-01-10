package kr.lunaf.nationSystem.repository;

import kr.lunaf.nationSystem.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

public class PlayerSettingsRepository {
    private final DatabaseManager databaseManager;

    public PlayerSettingsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<Boolean> getNationChatEnabled(UUID playerUuid) {
        String sql = "SELECT nation_chat_enabled FROM player_settings WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("nation_chat_enabled") == 1);
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setNationChatEnabled(Connection connection, UUID playerUuid, boolean enabled) throws Exception {
        String sql = "INSERT INTO player_settings (player_uuid, nation_chat_enabled) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE nation_chat_enabled = VALUES(nation_chat_enabled)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, enabled ? 1 : 0);
            statement.executeUpdate();
        }
    }
}
