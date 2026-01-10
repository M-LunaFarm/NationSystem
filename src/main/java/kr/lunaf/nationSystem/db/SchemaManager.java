package kr.lunaf.nationSystem.db;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

public class SchemaManager {
    private final DatabaseManager databaseManager;

    public SchemaManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void initialize() {
        List<String> statements = List.of(
            "CREATE TABLE IF NOT EXISTS nations (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(16) NOT NULL UNIQUE," +
                "owner_uuid CHAR(36) NOT NULL," +
                "level INT NOT NULL," +
                "exp BIGINT NOT NULL," +
                "bank_balance BIGINT NOT NULL," +
                "score INT NOT NULL," +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS nation_settings (" +
                "nation_id BIGINT PRIMARY KEY," +
                "pvp_enabled TINYINT NOT NULL DEFAULT 0," +
                "invite_lock TINYINT NOT NULL DEFAULT 0," +
                "chat_default TINYINT NOT NULL DEFAULT 0," +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS nation_members (" +
                "nation_id BIGINT NOT NULL," +
                "player_uuid CHAR(36) NOT NULL," +
                "role VARCHAR(16) NOT NULL," +
                "joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "PRIMARY KEY (nation_id, player_uuid)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS nation_territories (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "nation_id BIGINT NOT NULL," +
                "world VARCHAR(64) NOT NULL," +
                "center_x INT NOT NULL," +
                "center_y INT NOT NULL," +
                "center_z INT NOT NULL," +
                "size INT NOT NULL," +
                "wall_status VARCHAR(16) NOT NULL," +
                "wall_expires_at TIMESTAMP NULL," +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "INDEX idx_territory_nation (nation_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS nation_buildings (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "territory_id BIGINT NOT NULL," +
                "type VARCHAR(32) NOT NULL," +
                "state VARCHAR(16) NOT NULL," +
                "direction VARCHAR(8) NOT NULL," +
                "world VARCHAR(64) NOT NULL," +
                "base_x INT NOT NULL," +
                "base_y INT NOT NULL," +
                "base_z INT NOT NULL," +
                "level INT NOT NULL," +
                "build_complete_at TIMESTAMP NULL," +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "INDEX idx_building_territory (territory_id)," +
                "INDEX idx_building_state (state, build_complete_at)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS nation_daily_quests (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "nation_id BIGINT NOT NULL," +
                "quest_id INT NOT NULL," +
                "required_amount INT NOT NULL," +
                "progress_amount INT NOT NULL," +
                "completed TINYINT NOT NULL," +
                "quest_date DATE NOT NULL," +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "INDEX idx_daily_nation (nation_id, quest_date)," +
                "INDEX idx_daily_quest (nation_id, quest_id, quest_date)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS nation_bank_history (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "nation_id BIGINT NOT NULL," +
                "action VARCHAR(16) NOT NULL," +
                "amount BIGINT NOT NULL," +
                "actor_uuid CHAR(36) NOT NULL," +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "INDEX idx_bank_history (nation_id, created_at)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS nation_storage (" +
                "nation_id BIGINT PRIMARY KEY," +
                "contents MEDIUMTEXT," +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS nation_present_claims (" +
                "nation_id BIGINT PRIMARY KEY," +
                "last_claim_at TIMESTAMP NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS player_settings (" +
                "player_uuid CHAR(36) PRIMARY KEY," +
                "nation_chat_enabled TINYINT NOT NULL DEFAULT 0" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            connection.commit();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize schema", e);
        }
    }
}
