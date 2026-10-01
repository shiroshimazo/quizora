package quizora.DAO;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.ResultRecord;

/** Student result history includes archived quizzes, but never other students' records. */
public final class StudentResultsDAO {
    private final userDAO.Connections connections;
    public StudentResultsDAO() { this(databaseConnection::getReadOnlyConnection); }
    public StudentResultsDAO(userDAO.Connections connections) { this.connections = connections; }
    public List<ResultRecord> load(AuthenticatedUser user) throws SQLException {
        try (var connection = connections.open()) {
            connection.setAutoCommit(false);
            StudentProfileDAO.requireStudent(connection,user);
            List<ResultRecord> results = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    SELECT a.attempt_id,u.user_id,u.full_name,u.username,q.quiz_id,q.title,s.subject_id,s.subject_name,
                        r.score,r.total_points,a.submitted_at
                    FROM quiz_results r JOIN quiz_attempts a ON a.attempt_id=r.attempt_id
                    JOIN users u ON u.user_id=a.student_id JOIN quizzes q ON q.quiz_id=a.quiz_id
                    JOIN subjects s ON s.subject_id=q.subject_id
                    WHERE a.student_id=? AND a.status='submitted' AND a.submitted_at IS NOT NULL AND r.total_points>0
                    ORDER BY a.submitted_at DESC,a.attempt_id DESC
                    """)) {
                statement.setLong(1,user.id()); statement.setQueryTimeout(10);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) results.add(new ResultRecord(rows.getLong(1),rows.getLong(2),rows.getString(3),
                            rows.getString(4),rows.getLong(5),rows.getString(6),rows.getLong(7),rows.getString(8),
                            rows.getLong(9),rows.getLong(10),rows.getTimestamp(11).toLocalDateTime()));
                }
            }
            connection.commit();
            return List.copyOf(results);
        }
    }
}
