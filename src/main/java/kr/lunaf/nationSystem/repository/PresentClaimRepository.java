package kr.lunaf.nationSystem.repository;

import kr.lunaf.nationSystem.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;

public class PresentClaimRepository {
    private final DatabaseManager databaseManager;

    public PresentClaimRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<Instant> getLastClaim(long nationId) {
        String sql = "SELECT last_claim_at FROM nation_present_claims WHERE nation_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    java.sql.Timestamp ts = rs.getTimestamp("last_claim_at");
                    return Optional.ofNullable(ts).map(java.sql.Timestamp::toInstant);
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void upsertLastClaim(Connection connection, long nationId, Instant claimAt) throws Exception {
        String sql = "INSERT INTO nation_present_claims (nation_id, last_claim_at) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE last_claim_at = VALUES(last_claim_at)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.setTimestamp(2, java.sql.Timestamp.from(claimAt));
            statement.executeUpdate();
        }
    }
}
