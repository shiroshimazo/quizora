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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.StudentDashboardData;
import quizora.model.StudentDashboardData.AvailableQuiz;
import quizora.model.StudentDashboardData.RecentResult;
import quizora.model.StudentDashboardData.SubjectAverage;

public final class StudentDashboardDAO {
    /** A quiz a student can take: published, not archived, in an active subject, with questions. */
    private static final String AVAILABLE = """
            q.status='published' AND q.archived_at IS NULL AND s.archived_at IS NULL
            AND EXISTS (SELECT 1 FROM questions x WHERE x.quiz_id=q.quiz_id)
            """;
    /** Finalized attempts only; the same rule the admin and teacher result views use. */
    private static final String SCORED = """
            a.status='submitted' AND a.submitted_at IS NOT NULL AND r.total_points>0
            """;

    public StudentDashboardData load(AuthenticatedUser user) throws SQLException {
        if (user == null || !"student".equals(user.role())) {
            throw new SecurityException("Student access is required");
        }
        try (var connection = databaseConnection.getReadOnlyConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "SELECT user_id FROM users WHERE user_id=? AND role='student' AND is_active=TRUE AND archived_at IS NULL")) {
                statement.setQueryTimeout(10);
                statement.setLong(1, user.id());
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw new SecurityException("Student access is required");
                }
            }
            var data = readSnapshot(connection, user.id());
            connection.commit();
            return data;
        }
    }

    StudentDashboardData readSnapshot(Connection connection, long studentId) throws SQLException {
        long available, notAttempted, submissions, completed;
        Double average, best;
        try (var statement = connection.prepareStatement("""
                SELECT
                  (SELECT COUNT(*) FROM quizzes q JOIN subjects s ON s.subject_id=q.subject_id
                   WHERE %1$s) AS available,
                  (SELECT COUNT(*) FROM quizzes q JOIN subjects s ON s.subject_id=q.subject_id
                   WHERE %1$s AND NOT EXISTS (SELECT 1 FROM quiz_attempts a
                                              WHERE a.quiz_id=q.quiz_id AND a.student_id=?)) AS not_attempted,
                  (SELECT COUNT(*) FROM quiz_attempts WHERE student_id=? AND status='submitted') AS submissions,
                  (SELECT COUNT(DISTINCT quiz_id) FROM quiz_attempts
                   WHERE student_id=? AND status='submitted') AS completed,
                  (SELECT AVG(100.0*r.score/r.total_points) FROM quiz_results r
                   JOIN quiz_attempts a ON a.attempt_id=r.attempt_id WHERE a.student_id=? AND %2$s) AS average_score,
                  (SELECT MAX(100.0*r.score/r.total_points) FROM quiz_results r
                   JOIN quiz_attempts a ON a.attempt_id=r.attempt_id WHERE a.student_id=? AND %2$s) AS best_score
                """.formatted(AVAILABLE, SCORED))) {
            statement.setQueryTimeout(10);
            for (int i = 1; i <= 5; i++) statement.setLong(i, studentId);
            try (var result = statement.executeQuery()) {
                result.next();
                available = result.getLong("available");
                notAttempted = result.getLong("not_attempted");
                submissions = result.getLong("submissions");
                completed = result.getLong("completed");
                double score = result.getDouble("average_score");
                average = result.wasNull() ? null : score;
                score = result.getDouble("best_score");
                best = result.wasNull() ? null : score;
            }
        }
        List<AvailableQuiz> quizzes = new ArrayList<>();
        try (var statement = connection.prepareStatement("""
                SELECT q.quiz_id, q.title, s.subject_name, u.full_name,
                  (SELECT COUNT(*) FROM questions x WHERE x.quiz_id=q.quiz_id) AS questions,
                  q.time_limit_minutes,
                  (SELECT COUNT(*) FROM quiz_attempts a
                   WHERE a.quiz_id=q.quiz_id AND a.student_id=? AND a.status='submitted') AS attempts
                FROM quizzes q JOIN subjects s ON s.subject_id=q.subject_id JOIN users u ON u.user_id=q.teacher_id
                WHERE %s
                ORDER BY q.updated_at DESC, q.quiz_id DESC LIMIT 6
                """.formatted(AVAILABLE))) {
            statement.setQueryTimeout(10);
            statement.setLong(1, studentId);
            try (var result = statement.executeQuery()) {
                while (result.next()) quizzes.add(new AvailableQuiz(result.getLong(1), result.getString(2),
                        result.getString(3), result.getString(4), result.getInt(5), result.getInt(6), result.getLong(7)));
            }
        }
        List<RecentResult> recent = new ArrayList<>();
        try (var statement = connection.prepareStatement("""
                SELECT a.attempt_id, q.title, s.subject_name, r.score, r.total_points, a.submitted_at
                FROM quiz_results r JOIN quiz_attempts a ON a.attempt_id=r.attempt_id
                JOIN quizzes q ON q.quiz_id=a.quiz_id JOIN subjects s ON s.subject_id=q.subject_id
                WHERE a.student_id=? AND %s
                ORDER BY a.submitted_at DESC, a.attempt_id DESC LIMIT 5
                """.formatted(SCORED))) {
            statement.setQueryTimeout(10);
            statement.setLong(1, studentId);
            try (var result = statement.executeQuery()) {
                while (result.next()) recent.add(new RecentResult(result.getLong(1), result.getString(2),
                        result.getString(3), result.getLong(4), result.getLong(5),
                        result.getTimestamp(6).toLocalDateTime()));
            }
        }
        List<SubjectAverage> bySubject = new ArrayList<>();
        try (var statement = connection.prepareStatement("""
                SELECT s.subject_name, AVG(100.0*r.score/r.total_points) AS average, COUNT(*) AS attempts
                FROM quiz_results r JOIN quiz_attempts a ON a.attempt_id=r.attempt_id
                JOIN quizzes q ON q.quiz_id=a.quiz_id JOIN subjects s ON s.subject_id=q.subject_id
                WHERE a.student_id=? AND %s
                GROUP BY s.subject_id, s.subject_name
                ORDER BY attempts DESC, s.subject_name LIMIT 8
                """.formatted(SCORED))) {
            statement.setQueryTimeout(10);
            statement.setLong(1, studentId);
            try (var result = statement.executeQuery()) {
                while (result.next()) bySubject.add(new SubjectAverage(result.getString(1), result.getDouble(2),
                        result.getLong(3)));
            }
        }
        return new StudentDashboardData(available, notAttempted, submissions, completed, average, best,
                List.copyOf(quizzes), List.copyOf(recent), List.copyOf(bySubject));
    }
}
