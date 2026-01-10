package kr.lunaf.nationSystem.repository;

import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.DailyQuest;
import kr.lunaf.nationSystem.domain.DailyQuestType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DailyQuestRepository {
    private final DatabaseManager databaseManager;

    public DailyQuestRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<DailyQuest> listByNationAndDate(long nationId, LocalDate date) {
        String sql = "SELECT * FROM nation_daily_quests WHERE nation_id = ? AND quest_date = ?";
        List<DailyQuest> quests = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    quests.add(mapQuest(rs));
                }
            }
            return quests;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteByNationAndDate(Connection connection, long nationId, LocalDate date) throws Exception {
        String sql = "DELETE FROM nation_daily_quests WHERE nation_id = ? AND quest_date = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            statement.setDate(2, java.sql.Date.valueOf(date));
            statement.executeUpdate();
        }
    }

    public void insertQuest(Connection connection, DailyQuest quest) throws Exception {
        String sql = "INSERT INTO nation_daily_quests " +
            "(nation_id, quest_id, required_amount, progress_amount, completed, quest_date) " +
            "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, quest.nationId());
            statement.setInt(2, quest.type().id());
            statement.setInt(3, quest.requiredAmount());
            statement.setInt(4, quest.progressAmount());
            statement.setInt(5, quest.completed() ? 1 : 0);
            statement.setDate(6, java.sql.Date.valueOf(quest.questDate()));
            statement.executeUpdate();
        }
    }

    public void updateProgress(Connection connection, long nationId, int questId, LocalDate date, int progress, boolean completed) throws Exception {
        String sql = "UPDATE nation_daily_quests SET progress_amount = ?, completed = ? " +
            "WHERE nation_id = ? AND quest_id = ? AND quest_date = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, progress);
            statement.setInt(2, completed ? 1 : 0);
            statement.setLong(3, nationId);
            statement.setInt(4, questId);
            statement.setDate(5, java.sql.Date.valueOf(date));
            statement.executeUpdate();
        }
    }

    private DailyQuest mapQuest(ResultSet rs) throws Exception {
        int questId = rs.getInt("quest_id");
        DailyQuestType type = DailyQuestType.fromId(questId).orElseThrow();
        return new DailyQuest(
            rs.getLong("id"),
            rs.getLong("nation_id"),
            type,
            rs.getInt("required_amount"),
            rs.getInt("progress_amount"),
            rs.getInt("completed") == 1,
            rs.getDate("quest_date").toLocalDate()
        );
    }
}
