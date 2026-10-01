/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.DAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.QuizStatisticsData;
import quizora.model.QuizStatisticsData.Daily;
import quizora.model.QuizStatisticsData.Summary;

/** Attempts, completion and average score for every quiz of the signed-in teacher, including archived ones. */
public final class QuizStatisticsDAO {

    public QuizStatisticsData load(AuthenticatedUser teacher) throws SQLException {
        try (Connection connection = databaseConnection.getReadOnlyConnection()) {
            // One transaction so the table and the trend come from the same moment.
            connection.setAutoCommit(false);
            AccessCheck.requireTeacher(connection, teacher);
            LocalDate today = today(connection);
            QuizStatisticsData data = new QuizStatisticsData(today,
                    quizSummaries(connection, teacher.id()),
                    submissionsByDay(connection, teacher.id(), today));
            connection.commit();
            return data;
        }
    }

    private LocalDate today(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT date('now', 'localtime')");
                ResultSet result = statement.executeQuery()) {
            result.next();
            return LocalDate.parse(result.getString(1));
        }
    }

    /** One row per quiz, including quizzes with no attempts yet. */
    private List<Summary> quizSummaries(Connection connection, long teacherId) throws SQLException {
        List<Summary> quizzes = new ArrayList<>();
        String sql = "SELECT q.quiz_id, q.title, s.subject_name, q.status, q.archived_at, "
                + "COUNT(a.attempt_id) AS attempts, "
                + "COALESCE(SUM(a.status = 'submitted'), 0) AS submitted, "
                + "COUNT(r.result_id) AS scored, "
                + "AVG(100.0 * r.score / r.total_points) AS average_score "
                + "FROM quizzes q "
                + "JOIN subjects s ON s.subject_id = q.subject_id "
                + "LEFT JOIN quiz_attempts a ON a.quiz_id = q.quiz_id "
                + "LEFT JOIN quiz_results r ON r.attempt_id = a.attempt_id AND a.status = 'submitted' "
                + "AND a.submitted_at IS NOT NULL AND r.total_points > 0 "
                + "WHERE q.teacher_id = ? "
                + "GROUP BY q.quiz_id, q.title, s.subject_name, q.status, q.archived_at "
                + "ORDER BY q.quiz_id DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Double average = result.getDouble("average_score");
                    if (result.wasNull()) {
                        average = null;
                    }
                    quizzes.add(new Summary(
                            result.getLong("quiz_id"),
                            result.getString("title"),
                            result.getString("subject_name"),
                            result.getString("status"),
                            result.getString("archived_at") != null,
                            result.getLong("attempts"),
                            result.getLong("submitted"),
                            result.getLong("scored"),
                            average));
                }
            }
        }
        return quizzes;
    }

    /** Submissions per quiz per day for the last 14 days. Days with none are filled in by the screen. */
    private List<Daily> submissionsByDay(Connection connection, long teacherId, LocalDate today) throws SQLException {
        List<Daily> days = new ArrayList<>();
        String sql = "SELECT q.quiz_id, DATE(a.submitted_at) AS day, COUNT(*) AS total "
                + "FROM quiz_attempts a JOIN quizzes q ON q.quiz_id = a.quiz_id "
                + "WHERE q.teacher_id = ? AND a.status = 'submitted' "
                + "AND a.submitted_at >= ? AND a.submitted_at < ? "
                + "GROUP BY q.quiz_id, DATE(a.submitted_at) "
                + "ORDER BY day, q.quiz_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // Dates are stored as text, so the bounds are compared as 'YYYY-MM-DD' text too.
            statement.setLong(1, teacherId);
            statement.setString(2, today.minusDays(13).toString());
            statement.setString(3, today.plusDays(1).toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    days.add(new Daily(
                            result.getLong("quiz_id"),
                            LocalDate.parse(result.getString("day")),
                            result.getLong("total")));
                }
            }
        }
        return days;
    }
}
