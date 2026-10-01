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
import quizora.model.ResultRecord;

/** Scored quiz submissions: every student's for the admin, or only the teacher's own quizzes for a teacher. */
public final class ResultsDAO {

    private static final String SELECT_RESULTS = "SELECT a.attempt_id, u.user_id, u.full_name, u.username, "
            + "q.quiz_id, q.title, s.subject_id, s.subject_name, r.score, r.total_points, a.submitted_at "
            + "FROM quiz_results r "
            + "JOIN quiz_attempts a ON a.attempt_id = r.attempt_id "
            + "JOIN users u ON u.user_id = a.student_id "
            + "JOIN quizzes q ON q.quiz_id = a.quiz_id "
            + "JOIN subjects s ON s.subject_id = q.subject_id "
            + "WHERE a.status = 'submitted' AND a.submitted_at IS NOT NULL AND r.total_points > 0 ";
    private static final String NEWEST_FIRST = "ORDER BY a.submitted_at DESC, a.attempt_id DESC";

    public List<ResultRecord> load(AuthenticatedUser admin) throws SQLException {
        try (Connection connection = databaseConnection.getReadOnlyConnection()) {
            AccessCheck.requireAdmin(connection, admin);
            try (PreparedStatement statement = connection.prepareStatement(SELECT_RESULTS + NEWEST_FIRST)) {
                return readResults(statement);
            }
        }
    }

    public List<ResultRecord> loadForTeacher(AuthenticatedUser teacher) throws SQLException {
        try (Connection connection = databaseConnection.getReadOnlyConnection()) {
            AccessCheck.requireTeacher(connection, teacher);
            try (PreparedStatement statement = connection.prepareStatement(
                    SELECT_RESULTS + "AND q.teacher_id = ? " + NEWEST_FIRST)) {
                statement.setLong(1, teacher.id());
                return readResults(statement);
            }
        }
    }

    private List<ResultRecord> readResults(PreparedStatement statement) throws SQLException {
        List<ResultRecord> results = new ArrayList<>();
        try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                results.add(new ResultRecord(
                        result.getLong("attempt_id"),
                        result.getLong("user_id"),
                        result.getString("full_name"),
                        result.getString("username"),
                        result.getLong("quiz_id"),
                        result.getString("title"),
                        result.getLong("subject_id"),
                        result.getString("subject_name"),
                        result.getLong("score"),
                        result.getLong("total_points"),
                        result.getTimestamp("submitted_at").toLocalDateTime()));
            }
        }
        return results;
    }
}
