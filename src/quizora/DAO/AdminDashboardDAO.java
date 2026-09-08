package quizora.DAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.AdminDashboardData;
import quizora.model.AdminDashboardData.Count;
import quizora.model.AdminDashboardData.DailyCount;

public final class AdminDashboardDAO {
    public AdminDashboardData load(AuthenticatedUser user) throws SQLException {
        if (user == null || !"admin".equals(user.role())) {
            throw new SecurityException("Administrator access is required");
        }
        try (var connection = databaseConnection.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "SELECT user_id FROM users WHERE user_id=? AND role='admin' AND is_active=TRUE")) {
                statement.setQueryTimeout(10);
                statement.setLong(1, user.id());
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw new SecurityException("Administrator access is required");
                }
            }
            var data = readSnapshot(connection);
            connection.commit();
            return data;
        }
    }

    // Package access allows transaction-scoped integration fixtures without persistent sample data.
    AdminDashboardData readSnapshot(Connection connection) throws SQLException {
        long students, teachers, quizzes, subjects, submissions;
        Double average;
        LocalDate today;
        try (var statement = connection.prepareStatement("""
                SELECT
                  (SELECT COUNT(*) FROM users WHERE role='student') AS students,
                  (SELECT COUNT(*) FROM users WHERE role='teacher') AS teachers,
                  (SELECT COUNT(*) FROM quizzes) AS quizzes,
                  (SELECT COUNT(*) FROM subjects) AS subjects,
                  (SELECT COUNT(*) FROM quiz_attempts WHERE status='submitted') AS submissions,
                  (SELECT AVG(100.0*r.score/r.total_points) FROM quiz_results r
                   JOIN quiz_attempts a ON a.attempt_id=r.attempt_id
                   WHERE a.status='submitted') AS average_score,
                  CURRENT_DATE AS today
                """)) {
            statement.setQueryTimeout(10);
            try (var result = statement.executeQuery()) {
                result.next();
                students = result.getLong("students");
                teachers = result.getLong("teachers");
                quizzes = result.getLong("quizzes");
                subjects = result.getLong("subjects");
                submissions = result.getLong("submissions");
                double score = result.getDouble("average_score");
                average = result.wasNull() ? null : score;
                today = result.getDate("today").toLocalDate();
            }
        }
        List<Count> bySubject = new ArrayList<>();
        try (var statement = connection.prepareStatement("""
                SELECT s.subject_name, COUNT(q.quiz_id) AS total
                FROM subjects s LEFT JOIN quizzes q ON q.subject_id=s.subject_id
                GROUP BY s.subject_id, s.subject_name
                ORDER BY total DESC, s.subject_name LIMIT 8
                """)) {
            statement.setQueryTimeout(10);
            try (var result = statement.executeQuery()) {
                while (result.next()) bySubject.add(new Count(result.getString(1), result.getLong(2)));
            }
        }
        var days = new LinkedHashMap<LocalDate, Long>();
        for (int i = 13; i >= 0; i--) days.put(today.minusDays(i), 0L);
        try (var statement = connection.prepareStatement("""
                SELECT DATE(submitted_at), COUNT(*) FROM quiz_attempts
                WHERE status='submitted' AND submitted_at >= ? AND submitted_at < ?
                GROUP BY DATE(submitted_at) ORDER BY DATE(submitted_at)
                """)) {
            statement.setQueryTimeout(10);
            statement.setDate(1, java.sql.Date.valueOf(today.minusDays(13)));
            statement.setDate(2, java.sql.Date.valueOf(today.plusDays(1)));
            try (var result = statement.executeQuery()) {
                while (result.next()) days.put(result.getDate(1).toLocalDate(), result.getLong(2));
            }
        }
        var statuses = new LinkedHashMap<String, Long>();
        statuses.put("Draft", 0L);
        statuses.put("Published", 0L);
        statuses.put("Closed", 0L);
        try (var statement = connection.prepareStatement("SELECT status, COUNT(*) FROM quizzes GROUP BY status")) {
            statement.setQueryTimeout(10);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    String status = result.getString(1);
                    statuses.put(Character.toUpperCase(status.charAt(0)) + status.substring(1), result.getLong(2));
                }
            }
        }
        return new AdminDashboardData(students, teachers, quizzes, subjects, submissions, average, today,
                List.copyOf(bySubject),
                days.entrySet().stream().map(e -> new DailyCount(e.getKey(), e.getValue())).toList(),
                statuses.entrySet().stream().map(e -> new Count(e.getKey(), e.getValue())).toList());
    }
}
