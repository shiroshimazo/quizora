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
import quizora.model.TeacherDashboardData;
import quizora.model.TeacherDashboardData.Count;
import quizora.model.TeacherDashboardData.DailyCount;

/** Read-only numbers and charts for one teacher's dashboard. Archived quizzes are left out. */
public final class TeacherDashboardDAO {

    public TeacherDashboardData load(AuthenticatedUser teacher) throws SQLException {
        try (Connection connection = databaseConnection.getReadOnlyConnection()) {
            // One transaction so every number comes from the same moment.
            connection.setAutoCommit(false);
            AccessCheck.requireTeacher(connection, teacher);
            long teacherId = teacher.id();

            long students = count(connection, teacherId,
                    "SELECT COUNT(DISTINCT a.student_id) FROM quiz_attempts a "
                    + "JOIN quizzes q ON q.quiz_id = a.quiz_id "
                    + "WHERE q.teacher_id = ? AND q.archived_at IS NULL AND a.status = 'submitted'");
            long published = count(connection, teacherId,
                    "SELECT COUNT(*) FROM quizzes WHERE teacher_id = ? AND archived_at IS NULL AND status = 'published'");
            long quizzes = count(connection, teacherId,
                    "SELECT COUNT(*) FROM quizzes WHERE teacher_id = ? AND archived_at IS NULL");
            long subjects = count(connection, teacherId,
                    "SELECT COUNT(*) FROM teacher_subjects ts JOIN subjects s ON s.subject_id = ts.subject_id "
                    + "WHERE ts.teacher_id = ? AND s.archived_at IS NULL");
            long submissions = count(connection, teacherId,
                    "SELECT COUNT(*) FROM quiz_attempts a JOIN quizzes q ON q.quiz_id = a.quiz_id "
                    + "WHERE q.teacher_id = ? AND q.archived_at IS NULL AND a.status = 'submitted'");
            Double averageScore = averageScore(connection, teacherId);
            LocalDate today = today(connection);

            TeacherDashboardData data = new TeacherDashboardData(students, published, quizzes, subjects, submissions,
                    averageScore, today, quizzesBySubject(connection, teacherId),
                    submissionsByDay(connection, teacherId, today), quizStatuses(connection, teacherId));
            connection.commit();
            return data;
        }
    }

    private long count(Connection connection, long teacherId, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    /** Mean percentage of every scored submission on this teacher's quizzes, or null when there are none. */
    private Double averageScore(Connection connection, long teacherId) throws SQLException {
        String sql = "SELECT AVG(100.0 * r.score / r.total_points) FROM quiz_results r "
                + "JOIN quiz_attempts a ON a.attempt_id = r.attempt_id "
                + "JOIN quizzes q ON q.quiz_id = a.quiz_id "
                + "WHERE q.teacher_id = ? AND q.archived_at IS NULL AND a.status = 'submitted' AND r.total_points > 0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                double average = result.getDouble(1);
                if (result.wasNull()) {
                    return null;
                }
                return average;
            }
        }
    }

    private LocalDate today(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT date('now', 'localtime')");
                ResultSet result = statement.executeQuery()) {
            result.next();
            return LocalDate.parse(result.getString(1));
        }
    }

    /** Top eight subjects by this teacher's quiz count; ties are sorted by name. */
    private List<Count> quizzesBySubject(Connection connection, long teacherId) throws SQLException {
        List<Count> counts = new ArrayList<>();
        String sql = "SELECT s.subject_name, COUNT(q.quiz_id) AS total "
                + "FROM subjects s JOIN quizzes q ON q.subject_id = s.subject_id "
                + "WHERE q.teacher_id = ? AND q.archived_at IS NULL "
                + "GROUP BY s.subject_id, s.subject_name "
                + "ORDER BY total DESC, s.subject_name LIMIT 8";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    counts.add(new Count(result.getString("subject_name"), result.getLong("total")));
                }
            }
        }
        return counts;
    }

    /** Submitted attempts for each of the last 14 days, including days with none. */
    private List<DailyCount> submissionsByDay(Connection connection, long teacherId, LocalDate today)
            throws SQLException {
        Map<LocalDate, Long> days = new LinkedHashMap<>();
        for (int daysAgo = 13; daysAgo >= 0; daysAgo--) {
            days.put(today.minusDays(daysAgo), 0L);
        }
        String sql = "SELECT DATE(a.submitted_at) AS day, COUNT(*) AS total "
                + "FROM quiz_attempts a JOIN quizzes q ON q.quiz_id = a.quiz_id "
                + "WHERE q.teacher_id = ? AND q.archived_at IS NULL AND a.status = 'submitted' "
                + "AND a.submitted_at >= ? AND a.submitted_at < ? "
                + "GROUP BY DATE(a.submitted_at)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // Dates are stored as text, so the bounds are compared as 'YYYY-MM-DD' text too.
            statement.setLong(1, teacherId);
            statement.setString(2, today.minusDays(13).toString());
            statement.setString(3, today.plusDays(1).toString());
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

    /** This teacher's quiz counts for Draft, Published and Closed, in that order. */
    private List<Count> quizStatuses(Connection connection, long teacherId) throws SQLException {
        Map<String, Long> statuses = new LinkedHashMap<>();
        statuses.put("Draft", 0L);
        statuses.put("Published", 0L);
        statuses.put("Closed", 0L);
        String sql = "SELECT status, COUNT(*) AS total FROM quizzes "
                + "WHERE teacher_id = ? AND archived_at IS NULL GROUP BY status";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String status = result.getString("status");
                    String label = status.substring(0, 1).toUpperCase() + status.substring(1);
                    statuses.put(label, result.getLong("total"));
                }
            }
        }
        List<Count> counts = new ArrayList<>();
        for (Map.Entry<String, Long> status : statuses.entrySet()) {
            counts.add(new Count(status.getKey(), status.getValue()));
        }
        return counts;
    }
}
