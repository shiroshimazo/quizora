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

/** Quizzes a student can take. Never exposes correct answers or other students' attempts. */
public final class AvailableQuizDAO {

    public record Quiz(long id, String title, String subject, String teacher, String description,
            int questions, int minutes, long points, long attempts, boolean inProgress) { }

    private final userDAO.Connections connections;

    public AvailableQuizDAO() {
        this(databaseConnection::getReadOnlyConnection);
    }

    public AvailableQuizDAO(userDAO.Connections connections) {
        this.connections = connections;
    }

    /** Published, not archived, in an active subject, and with at least one question. */
    public List<Quiz> load(AuthenticatedUser student) throws SQLException {
        List<Quiz> quizzes = new ArrayList<>();
        String sql = "SELECT q.quiz_id, q.title, s.subject_name, u.full_name, "
                + "COALESCE(q.description, '') AS description, q.time_limit_minutes, "
                + "(SELECT COUNT(*) FROM questions x WHERE x.quiz_id = q.quiz_id) AS questions, "
                + "(SELECT SUM(points) FROM questions x WHERE x.quiz_id = q.quiz_id) AS points, "
                + "(SELECT COUNT(*) FROM quiz_attempts a WHERE a.quiz_id = q.quiz_id "
                + "AND a.student_id = ? AND a.status = 'submitted') AS attempts, "
                + "EXISTS (SELECT 1 FROM quiz_attempts a WHERE a.quiz_id = q.quiz_id "
                + "AND a.student_id = ? AND a.status = 'in_progress') AS in_progress "
                + "FROM quizzes q "
                + "JOIN subjects s ON s.subject_id = q.subject_id "
                + "JOIN users u ON u.user_id = q.teacher_id "
                + "WHERE q.status = 'published' AND q.archived_at IS NULL AND s.archived_at IS NULL "
                + "AND EXISTS (SELECT 1 FROM questions x WHERE x.quiz_id = q.quiz_id) "
                + "ORDER BY q.updated_at DESC, q.quiz_id DESC";
        try (Connection connection = connections.open()) {
            AccessCheck.requireStudent(connection, student);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, student.id());
                statement.setLong(2, student.id());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        quizzes.add(new Quiz(
                                result.getLong("quiz_id"),
                                result.getString("title"),
                                result.getString("subject_name"),
                                result.getString("full_name"),
                                result.getString("description"),
                                result.getInt("questions"),
                                result.getInt("time_limit_minutes"),
                                result.getLong("points"),
                                result.getLong("attempts"),
                                result.getBoolean("in_progress")));
                    }
                }
            }
        }
        return quizzes;
    }
}
