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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.AdminDashboardData;
import quizora.model.AdminDashboardData.Count;
import quizora.model.AdminDashboardData.DailyCount;

/** Read-only numbers and charts for the administrator dashboard. */
public final class AdminDashboardDAO {

    public AdminDashboardData load(AuthenticatedUser admin) throws SQLException {
        try (Connection connection = databaseConnection.getReadOnlyConnection()) {
            // One transaction so every number comes from the same moment.
            connection.setAutoCommit(false);
            AccessCheck.requireAdmin(connection, admin);

            long students = count(connection, "SELECT COUNT(*) FROM users WHERE role = 'student'");
            long teachers = count(connection, "SELECT COUNT(*) FROM users WHERE role = 'teacher'");
            long quizzes = count(connection, "SELECT COUNT(*) FROM quizzes");
            long subjects = count(connection, "SELECT COUNT(*) FROM subjects");
            long submissions = count(connection, "SELECT COUNT(*) FROM quiz_attempts WHERE status = 'submitted'");
            Double averageScore = averageScore(connection);
            LocalDate today = today(connection);

            AdminDashboardData data = new AdminDashboardData(students, teachers, quizzes, subjects, submissions,
                    averageScore, today, quizzesBySubject(connection), submissionsByDay(connection, today),
                    quizStatuses(connection));
            connection.commit();
            return data;
        }
    }

    private long count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    /** Mean percentage of every scored submission, or null when there are none. */
    private Double averageScore(Connection connection) throws SQLException {
        String sql = "SELECT AVG(100.0 * r.score / r.total_points) FROM quiz_results r "
                + "JOIN quiz_attempts a ON a.attempt_id = r.attempt_id "
                + "WHERE a.status = 'submitted'";
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            result.next();
            double average = result.getDouble(1);
            if (result.wasNull()) {
                return null;
            }
            return average;
        }
    }

    private LocalDate today(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT date('now', 'localtime')");
                ResultSet result = statement.executeQuery()) {
            result.next();
            return LocalDate.parse(result.getString(1));
        }
    }

    /** Top eight subjects by number of quizzes; ties are sorted by name. */
    private List<Count> quizzesBySubject(Connection connection) throws SQLException {
        List<Count> counts = new ArrayList<>();
        String sql = "SELECT s.subject_name, COUNT(q.quiz_id) AS total "
                + "FROM subjects s LEFT JOIN quizzes q ON q.subject_id = s.subject_id "
                + "GROUP BY s.subject_id, s.subject_name "
                + "ORDER BY total DESC, s.subject_name LIMIT 8";
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                counts.add(new Count(result.getString("subject_name"), result.getLong("total")));
            }
        }
        return counts;
    }

    /** Submitted attempts for each of the last 14 days, including days with none. */
    private List<DailyCount> submissionsByDay(Connection connection, LocalDate today) throws SQLException {
        Map<LocalDate, Long> days = new LinkedHashMap<>();
        for (int daysAgo = 13; daysAgo >= 0; daysAgo--) {
            days.put(today.minusDays(daysAgo), 0L);
        }
        String sql = "SELECT DATE(submitted_at) AS day, COUNT(*) AS total FROM quiz_attempts "
                + "WHERE status = 'submitted' AND submitted_at >= ? AND submitted_at < ? "
                + "GROUP BY DATE(submitted_at)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // Dates are stored as text, so the bounds are compared as 'YYYY-MM-DD' text too.
            statement.setString(1, today.minusDays(13).toString());
            statement.setString(2, today.plusDays(1).toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    days.put(LocalDate.parse(result.getString("day")), result.getLong("total"));
                }
            }
        }
        List<DailyCount> counts = new ArrayList<>();
        for (Map.Entry<LocalDate, Long> day : days.entrySet()) {
            counts.add(new DailyCount(day.getKey(), day.getValue()));
        }
        return counts;
    }

    /** Quiz counts for Draft, Published and Closed, in that order. */
    private List<Count> quizStatuses(Connection connection) throws SQLException {
        Map<String, Long> statuses = new LinkedHashMap<>();
        statuses.put("Draft", 0L);
        statuses.put("Published", 0L);
        statuses.put("Closed", 0L);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status, COUNT(*) AS total FROM quizzes GROUP BY status");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String status = result.getString("status");
                String label = status.substring(0, 1).toUpperCase() + status.substring(1);
                statuses.put(label, result.getLong("total"));
            }
        }
        List<Count> counts = new ArrayList<>();
        for (Map.Entry<String, Long> status : statuses.entrySet()) {
            counts.add(new Count(status.getKey(), status.getValue()));
        }
        return counts;
    }
}
