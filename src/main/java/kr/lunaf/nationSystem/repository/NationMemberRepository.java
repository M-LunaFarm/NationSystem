package kr.lunaf.nationSystem.repository;

import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.NationMember;
import kr.lunaf.nationSystem.domain.NationRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class NationMemberRepository {
    private final DatabaseManager databaseManager;

    public NationMemberRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<NationMember> findByPlayer(UUID playerUuid) {
        String sql = "SELECT nation_id, player_uuid, role FROM nation_members WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapMember(rs));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void insertMember(Connection connection, long nationId, UUID playerUuid, NationRole role) throws Exception {
        String sql = "INSERT INTO nation_members (nation_id, player_uuid, role) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.setString(2, playerUuid.toString());
            statement.setString(3, role.name());
            statement.executeUpdate();
        }
    }

    public void deleteMember(Connection connection, long nationId, UUID playerUuid) throws Exception {
        String sql = "DELETE FROM nation_members WHERE nation_id = ? AND player_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.setString(2, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    public void deleteByNation(Connection connection, long nationId) throws Exception {
        String sql = "DELETE FROM nation_members WHERE nation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.executeUpdate();
        }
    }

    public List<UUID> listMemberUuids(long nationId) {
        String sql = "SELECT player_uuid FROM nation_members WHERE nation_id = ?";
        List<UUID> members = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    members.add(UUID.fromString(rs.getString("player_uuid")));
                }
            }
            return members;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private NationMember mapMember(ResultSet rs) throws Exception {
        return new NationMember(
            rs.getLong("nation_id"),
            UUID.fromString(rs.getString("player_uuid")),
            NationRole.valueOf(rs.getString("role"))
        );
    }
}
