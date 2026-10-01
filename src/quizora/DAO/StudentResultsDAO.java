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

/** The signed-in student's own scored submissions, newest first. Archived quizzes stay in the history. */
public final class StudentResultsDAO {
    private final userDAO.Connections connections;

    public StudentResultsDAO() {
        this(databaseConnection::getReadOnlyConnection);
    }

    public StudentResultsDAO(userDAO.Connections connections) {
        this.connections = connections;
    }

    public List<ResultRecord> load(AuthenticatedUser student) throws SQLException {
        List<ResultRecord> results = new ArrayList<>();
        String sql = "SELECT a.attempt_id, u.user_id, u.full_name, u.username, q.quiz_id, q.title, "
                + "s.subject_id, s.subject_name, r.score, r.total_points, a.submitted_at "
                + "FROM quiz_results r "
                + "JOIN quiz_attempts a ON a.attempt_id = r.attempt_id "
                + "JOIN users u ON u.user_id = a.student_id "
                + "JOIN quizzes q ON q.quiz_id = a.quiz_id "
                + "JOIN subjects s ON s.subject_id = q.subject_id "
                + "WHERE a.student_id = ? AND a.status = 'submitted' AND a.submitted_at IS NOT NULL "
                + "AND r.total_points > 0 "
                + "ORDER BY a.submitted_at DESC, a.attempt_id DESC";
        try (Connection connection = connections.open()) {
            AccessCheck.requireStudent(connection, student);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, student.id());
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
            }
        }
        return results;
    }
}
