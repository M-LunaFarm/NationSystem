package kr.lunaf.nationSystem.repository;

import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.BankHistoryEntry;
import kr.lunaf.nationSystem.domain.BankHistoryType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BankHistoryRepository {
    private final DatabaseManager databaseManager;

    public BankHistoryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void insert(Connection connection, long nationId, BankHistoryType type, long amount, UUID actorUuid) throws Exception {
        String sql = "INSERT INTO nation_bank_history (nation_id, action, amount, actor_uuid) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, nationId);
            statement.setString(2, type.name());
            statement.setLong(3, amount);
            statement.setString(4, actorUuid.toString());
            statement.executeUpdate();
        }
    }

    public List<BankHistoryEntry> listRecent(long nationId, int limit) {
        String sql = "SELECT * FROM nation_bank_history WHERE nation_id = ? ORDER BY created_at DESC LIMIT ?";
        List<BankHistoryEntry> entries = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapEntry(rs));
                }
            }
            return entries;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BankHistoryEntry mapEntry(ResultSet rs) throws Exception {
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        return new BankHistoryEntry(
            rs.getLong("id"),
            rs.getLong("nation_id"),
            BankHistoryType.valueOf(rs.getString("action")),
            rs.getLong("amount"),
            UUID.fromString(rs.getString("actor_uuid")),
            createdAt
        );
    }
}
