package kr.lunaf.nationSystem.repository;

import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.Nation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

public class NationRepository {
    private final DatabaseManager databaseManager;

    public NationRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<Nation> findByName(String name) {
        String sql = "SELECT id, name, owner_uuid, level, exp, bank_balance, score FROM nations WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapNation(rs));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Nation> findById(long id) {
        String sql = "SELECT id, name, owner_uuid, level, exp, bank_balance, score FROM nations WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapNation(rs));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Nation> findByMember(UUID playerUuid) {
        String sql = "SELECT n.id, n.name, n.owner_uuid, n.level, n.exp, n.bank_balance, n.score " +
            "FROM nations n JOIN nation_members m ON n.id = m.nation_id WHERE m.player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapNation(rs));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public long insertNation(Connection connection, Nation nation) throws Exception {
        String sql = "INSERT INTO nations (name, owner_uuid, level, exp, bank_balance, score) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, nation.name());
            statement.setString(2, nation.ownerUuid().toString());
            statement.setInt(3, nation.level());
            statement.setLong(4, nation.exp());
            statement.setLong(5, nation.bankBalance());
            statement.setInt(6, nation.score());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new IllegalStateException("No generated key for nation");
    }

    public int countMembers(long nationId) {
        String sql = "SELECT COUNT(*) FROM nation_members WHERE nation_id = ?";
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

    public void deleteNation(Connection connection, long nationId) throws Exception {
        String sql = "DELETE FROM nations WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.executeUpdate();
        }
    }

    public void addExp(Connection connection, long nationId, long amount) throws Exception {
        String sql = "UPDATE nations SET exp = exp + ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, amount);
            statement.setLong(2, nationId);
            statement.executeUpdate();
        }
    }

    public long getBankBalanceForUpdate(Connection connection, long nationId) throws Exception {
        String sql = "SELECT bank_balance FROM nations WHERE id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        }
    }

    public void addBankBalance(Connection connection, long nationId, long amount) throws Exception {
        String sql = "UPDATE nations SET bank_balance = bank_balance + ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, amount);
            statement.setLong(2, nationId);
            statement.executeUpdate();
        }
    }

    public void levelUpNation(Connection connection, long nationId, long expCost, long moneyCost) throws Exception {
        String sql = "UPDATE nations SET level = level + 1, exp = exp - ?, bank_balance = bank_balance - ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, expCost);
            statement.setLong(2, moneyCost);
            statement.setLong(3, nationId);
            statement.executeUpdate();
        }
    }

    private Nation mapNation(ResultSet rs) throws Exception {
        return new Nation(
            rs.getLong("id"),
            rs.getString("name"),
            UUID.fromString(rs.getString("owner_uuid")),
            rs.getInt("level"),
            rs.getLong("exp"),
            rs.getLong("bank_balance"),
            rs.getInt("score")
        );
    }
}
