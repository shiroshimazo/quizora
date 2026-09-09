package quizora.DAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.TeacherDashboardData;
import quizora.model.TeacherDashboardData.Count;
import quizora.model.TeacherDashboardData.DailyCount;

public final class TeacherDashboardDAO {
    public TeacherDashboardData load(AuthenticatedUser user) throws SQLException {
        if (user == null || !"teacher".equals(user.role())) {
            throw new SecurityException("Teacher access is required");
        }
        try (var connection = databaseConnection.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "SELECT user_id FROM users WHERE user_id=? AND role='teacher' AND is_active=TRUE AND archived_at IS NULL")) {
                statement.setQueryTimeout(10);
                statement.setLong(1, user.id());
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw new SecurityException("Teacher access is required");
                }
            }
            var data = readSnapshot(connection, user.id());
            connection.commit();
            return data;
        }
    }

    // Package access allows transaction-scoped integration fixtures without persistent sample data.
    TeacherDashboardData readSnapshot(Connection connection, long teacherId) throws SQLException {
        long students, published, quizzes, subjects, submissions;
        Double average;
        LocalDate today;
        try (var statement = connection.prepareStatement("""
                SELECT
                  (SELECT COUNT(DISTINCT a.student_id) FROM quiz_attempts a JOIN quizzes q ON q.quiz_id=a.quiz_id
                   WHERE q.teacher_id=? AND q.archived_at IS NULL AND a.status='submitted') AS students,
                  (SELECT COUNT(*) FROM quizzes WHERE teacher_id=? AND archived_at IS NULL AND status='published') AS published,
                  (SELECT COUNT(*) FROM quizzes WHERE teacher_id=? AND archived_at IS NULL) AS quizzes,
                  (SELECT COUNT(*) FROM teacher_subjects ts JOIN subjects s ON s.subject_id=ts.subject_id
                   WHERE ts.teacher_id=? AND s.archived_at IS NULL) AS subjects,
                  (SELECT COUNT(*) FROM quiz_attempts a JOIN quizzes q ON q.quiz_id=a.quiz_id
                   WHERE q.teacher_id=? AND q.archived_at IS NULL AND a.status='submitted') AS submissions,
                  (SELECT AVG(100.0*r.score/NULLIF(r.total_points,0)) FROM quiz_results r
                   JOIN quiz_attempts a ON a.attempt_id=r.attempt_id JOIN quizzes q ON q.quiz_id=a.quiz_id
                   WHERE q.teacher_id=? AND q.archived_at IS NULL AND a.status='submitted') AS average_score,
                  CURRENT_DATE AS today
                """)) {
            statement.setQueryTimeout(10);
            for (int i = 1; i <= 6; i++) statement.setLong(i, teacherId);
            try (var result = statement.executeQuery()) {
                result.next();
                students = result.getLong("students");
                published = result.getLong("published");
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
                FROM subjects s JOIN quizzes q ON q.subject_id=s.subject_id
                WHERE q.teacher_id=? AND q.archived_at IS NULL
                GROUP BY s.subject_id, s.subject_name
                ORDER BY total DESC, s.subject_name LIMIT 8
                """)) {
            statement.setQueryTimeout(10);
            statement.setLong(1, teacherId);
            try (var result = statement.executeQuery()) {
                while (result.next()) bySubject.add(new Count(result.getString(1), result.getLong(2)));
            }
        }
        var days = new LinkedHashMap<LocalDate, Long>();
        for (int i = 13; i >= 0; i--) days.put(today.minusDays(i), 0L);
        try (var statement = connection.prepareStatement("""
                SELECT DATE(submitted_at), COUNT(*) FROM quiz_attempts a JOIN quizzes q ON q.quiz_id=a.quiz_id
                WHERE q.teacher_id=? AND q.archived_at IS NULL AND a.status='submitted' AND submitted_at >= ? AND submitted_at < ?
                GROUP BY DATE(submitted_at) ORDER BY DATE(submitted_at)
                """)) {
            statement.setQueryTimeout(10);
            statement.setLong(1, teacherId);
            statement.setDate(2, java.sql.Date.valueOf(today.minusDays(13)));
            statement.setDate(3, java.sql.Date.valueOf(today.plusDays(1)));
            try (var result = statement.executeQuery()) {
                while (result.next()) days.put(result.getDate(1).toLocalDate(), result.getLong(2));
            }
        }
        var statuses = new LinkedHashMap<String, Long>();
        statuses.put("Draft", 0L);
        statuses.put("Published", 0L);
        statuses.put("Closed", 0L);
        try (var statement = connection.prepareStatement("SELECT status, COUNT(*) FROM quizzes WHERE teacher_id=? AND archived_at IS NULL GROUP BY status")) {
            statement.setQueryTimeout(10);
            statement.setLong(1, teacherId);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    String status = result.getString(1);
                    statuses.put(Character.toUpperCase(status.charAt(0)) + status.substring(1), result.getLong(2));
                }
            }
        }
        return new TeacherDashboardData(students, published, quizzes, subjects, submissions, average, today,
                List.copyOf(bySubject),
                days.entrySet().stream().map(e -> new DailyCount(e.getKey(), e.getValue())).toList(),
                statuses.entrySet().stream().map(e -> new Count(e.getKey(), e.getValue())).toList());
    }
}
