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
import java.time.LocalDateTime;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.ReportData;

/** All-time quiz, student and teacher statistics for the admin Reports page and its PDF export. */
public final class ReportsDAO {

    public ReportData load(AuthenticatedUser admin, int passThreshold) throws SQLException {
        if (passThreshold < 0 || passThreshold > 100) {
            throw new IllegalArgumentException("Passing score must be between 0 and 100.");
        }
        try (Connection connection = databaseConnection.getReadOnlyConnection()) {
            // One transaction so every number comes from the same moment.
            connection.setAutoCommit(false);
            AccessCheck.requireAdmin(connection, admin);

            ReportData.Accounts students = accounts(connection, "student");
            ReportData.Accounts teachers = accounts(connection, "teacher");
            LocalDateTime generatedAt = now(connection);
            long quizzes = count(connection, "SELECT COUNT(*) FROM quizzes");
            long published = count(connection, "SELECT COUNT(*) FROM quizzes WHERE status = 'published' AND archived_at IS NULL");
            long archivedQuizzes = count(connection, "SELECT COUNT(*) FROM quizzes WHERE archived_at IS NOT NULL");
            long attempts = count(connection, "SELECT COUNT(*) FROM quiz_attempts");
            long submitted = count(connection, "SELECT COUNT(*) FROM quiz_attempts WHERE status = 'submitted'");

            // Scores use submitted attempts with a finalized result, as percentages.
            String scoreSql = "SELECT COUNT(*), "
                    + "AVG(100.0 * r.score / r.total_points), "
                    + "MIN(100.0 * r.score / r.total_points), "
                    + "MAX(100.0 * r.score / r.total_points), "
                    + "AVG(CASE WHEN 100.0 * r.score / r.total_points >= ? THEN 100.0 ELSE 0 END) "
                    + "FROM quiz_results r JOIN quiz_attempts a ON a.attempt_id = r.attempt_id "
                    + "WHERE a.status = 'submitted' AND r.total_points > 0";
            ReportData data;
            try (PreparedStatement statement = connection.prepareStatement(scoreSql)) {
                statement.setInt(1, passThreshold);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    long scored = result.getLong(1);
                    Double average = numberOrNull(result, 2);
                    Double lowest = numberOrNull(result, 3);
                    Double highest = numberOrNull(result, 4);
                    Double passRate = numberOrNull(result, 5);
                    data = new ReportData(generatedAt, passThreshold, students, teachers, quizzes, published,
                            archivedQuizzes, attempts, submitted, scored, average, lowest, highest, passRate);
                }
            }
            connection.commit();
            return data;
        }
    }

    /** Total, active, inactive and archived accounts for one role. */
    private ReportData.Accounts accounts(Connection connection, String role) throws SQLException {
        String sql = "SELECT COUNT(*), "
                + "COALESCE(SUM(is_active = 1 AND archived_at IS NULL), 0), "
                + "COALESCE(SUM(is_active = 0 AND archived_at IS NULL), 0), "
                + "COALESCE(SUM(archived_at IS NOT NULL), 0) "
                + "FROM users WHERE role = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, role);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return new ReportData.Accounts(result.getLong(1), result.getLong(2), result.getLong(3), result.getLong(4));
            }
        }
    }

    private long count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private LocalDateTime now(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT datetime('now', 'localtime')");
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getTimestamp(1).toLocalDateTime();
        }
    }

    /** SQL averages are NULL when there are no rows; the report shows that as N/A. */
    private Double numberOrNull(ResultSet result, int column) throws SQLException {
        double value = result.getDouble(column);
        if (result.wasNull()) {
            return null;
        }
        return value;
    }
}
