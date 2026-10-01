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
import java.util.ArrayList;
import java.util.List;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.StudentDashboardData;
import quizora.model.StudentDashboardData.AvailableQuiz;
import quizora.model.StudentDashboardData.RecentResult;
import quizora.model.StudentDashboardData.SubjectAverage;

/** Read-only numbers, tables and chart for the signed-in student's dashboard. */
public final class StudentDashboardDAO {

    /** A quiz a student can take: published, not archived, in an active subject, with questions. Needs "quizzes q" and "subjects s". */
    private static final String AVAILABLE = "q.status = 'published' AND q.archived_at IS NULL AND s.archived_at IS NULL "
            + "AND EXISTS (SELECT 1 FROM questions x WHERE x.quiz_id = q.quiz_id) ";

    /** A finalized, scored submission. Needs "quiz_attempts a" and "quiz_results r". */
    private static final String SCORED = "a.status = 'submitted' AND a.submitted_at IS NOT NULL AND r.total_points > 0 ";

    public StudentDashboardData load(AuthenticatedUser student) throws SQLException {
        try (Connection connection = databaseConnection.getReadOnlyConnection()) {
            // One transaction so every number comes from the same moment.
            connection.setAutoCommit(false);
            AccessCheck.requireStudent(connection, student);
            long studentId = student.id();

            long available = count(connection,
                    "SELECT COUNT(*) FROM quizzes q JOIN subjects s ON s.subject_id = q.subject_id WHERE " + AVAILABLE);
            long notAttempted = countForStudent(connection, studentId,
                    "SELECT COUNT(*) FROM quizzes q JOIN subjects s ON s.subject_id = q.subject_id WHERE " + AVAILABLE
                    + "AND NOT EXISTS (SELECT 1 FROM quiz_attempts a WHERE a.quiz_id = q.quiz_id AND a.student_id = ?)");
            long submissions = countForStudent(connection, studentId,
                    "SELECT COUNT(*) FROM quiz_attempts WHERE student_id = ? AND status = 'submitted'");
            long completedQuizzes = countForStudent(connection, studentId,
                    "SELECT COUNT(DISTINCT quiz_id) FROM quiz_attempts WHERE student_id = ? AND status = 'submitted'");
            Double averageScore = scoreForStudent(connection, studentId, "AVG");
            Double bestScore = scoreForStudent(connection, studentId, "MAX");

            StudentDashboardData data = new StudentDashboardData(available, notAttempted, submissions, completedQuizzes,
                    averageScore, bestScore, availableQuizzes(connection, studentId),
                    recentResults(connection, studentId), averageBySubject(connection, studentId));
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

    /** Runs a COUNT query whose only "?" is the student ID. */
    private long countForStudent(Connection connection, long studentId, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    /** The student's average (AVG) or best (MAX) percentage, or null when they have no scores. */
    private Double scoreForStudent(Connection connection, long studentId, String function) throws SQLException {
        String sql = "SELECT " + function + "(100.0 * r.score / r.total_points) FROM quiz_results r "
                + "JOIN quiz_attempts a ON a.attempt_id = r.attempt_id "
                + "WHERE a.student_id = ? AND " + SCORED;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                double score = result.getDouble(1);
                if (result.wasNull()) {
                    return null;
                }
                return score;
            }
        }
    }

    /** The six most recently updated quizzes the student can take. */
    private List<AvailableQuiz> availableQuizzes(Connection connection, long studentId) throws SQLException {
        List<AvailableQuiz> quizzes = new ArrayList<>();
        String sql = "SELECT q.quiz_id, q.title, s.subject_name, u.full_name, q.time_limit_minutes, "
                + "(SELECT COUNT(*) FROM questions x WHERE x.quiz_id = q.quiz_id) AS questions, "
                + "(SELECT COUNT(*) FROM quiz_attempts a WHERE a.quiz_id = q.quiz_id "
                + "AND a.student_id = ? AND a.status = 'submitted') AS attempts "
                + "FROM quizzes q "
                + "JOIN subjects s ON s.subject_id = q.subject_id "
                + "JOIN users u ON u.user_id = q.teacher_id "
                + "WHERE " + AVAILABLE
                + "ORDER BY q.updated_at DESC, q.quiz_id DESC LIMIT 6";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    quizzes.add(new AvailableQuiz(
                            result.getLong("quiz_id"),
                            result.getString("title"),
                            result.getString("subject_name"),
                            result.getString("full_name"),
                            result.getInt("questions"),
                            result.getInt("time_limit_minutes"),
                            result.getLong("attempts")));
                }
            }
        }
        return quizzes;
    }

    /** The student's five latest scored submissions. */
    private List<RecentResult> recentResults(Connection connection, long studentId) throws SQLException {
        List<RecentResult> results = new ArrayList<>();
        String sql = "SELECT a.attempt_id, q.title, s.subject_name, r.score, r.total_points, a.submitted_at "
                + "FROM quiz_results r "
                + "JOIN quiz_attempts a ON a.attempt_id = r.attempt_id "
                + "JOIN quizzes q ON q.quiz_id = a.quiz_id "
                + "JOIN subjects s ON s.subject_id = q.subject_id "
                + "WHERE a.student_id = ? AND " + SCORED
                + "ORDER BY a.submitted_at DESC, a.attempt_id DESC LIMIT 5";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    results.add(new RecentResult(
                            result.getLong("attempt_id"),
                            result.getString("title"),
                            result.getString("subject_name"),
                            result.getLong("score"),
                            result.getLong("total_points"),
                            result.getTimestamp("submitted_at").toLocalDateTime()));
                }
            }
        }
        return results;
    }

    /** Average percentage per subject for the eight subjects with the most attempts. */
    private List<SubjectAverage> averageBySubject(Connection connection, long studentId) throws SQLException {
        List<SubjectAverage> averages = new ArrayList<>();
        String sql = "SELECT s.subject_name, AVG(100.0 * r.score / r.total_points) AS average, COUNT(*) AS attempts "
                + "FROM quiz_results r "
                + "JOIN quiz_attempts a ON a.attempt_id = r.attempt_id "
                + "JOIN quizzes q ON q.quiz_id = a.quiz_id "
                + "JOIN subjects s ON s.subject_id = q.subject_id "
                + "WHERE a.student_id = ? AND " + SCORED
                + "GROUP BY s.subject_id, s.subject_name "
                + "ORDER BY attempts DESC, s.subject_name LIMIT 8";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    averages.add(new SubjectAverage(
                            result.getString("subject_name"),
                            result.getDouble("average"),
                            result.getLong("attempts")));
                }
            }
        }
        return averages;
    }
}
