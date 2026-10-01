package quizora.DAO;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;

/** Read-only catalogue; never exposes answers or another student's attempts. */
public final class AvailableQuizDAO {
    public record Quiz(long id, String title, String subject, String teacher, String description,
            int questions, int minutes, long points, long attempts, boolean inProgress) { }
    private final userDAO.Connections connections;
    public AvailableQuizDAO() { this(databaseConnection::getReadOnlyConnection); }
    public AvailableQuizDAO(userDAO.Connections connections) { this.connections = connections; }

    public List<Quiz> load(AuthenticatedUser user) throws SQLException {
        if (user == null || !"student".equals(user.role())) throw new SecurityException("Student access is required.");
        try (var connection = connections.open()) {
            connection.setAutoCommit(false);
            try (var check = connection.prepareStatement("SELECT 1 FROM users WHERE user_id=? AND role='student' AND is_active=1 AND archived_at IS NULL")) {
                check.setLong(1, user.id());
                try (var rows = check.executeQuery()) {
                    if (!rows.next()) throw new SecurityException("Student access is required.");
                }
            }
            List<Quiz> quizzes = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    SELECT q.quiz_id,q.title,s.subject_name,u.full_name,COALESCE(q.description,''),
                        (SELECT COUNT(*) FROM questions x WHERE x.quiz_id=q.quiz_id),q.time_limit_minutes,
                        (SELECT SUM(points) FROM questions x WHERE x.quiz_id=q.quiz_id),
                        (SELECT COUNT(*) FROM quiz_attempts a WHERE a.quiz_id=q.quiz_id AND a.student_id=? AND a.status='submitted'),
                        EXISTS(SELECT 1 FROM quiz_attempts a WHERE a.quiz_id=q.quiz_id AND a.student_id=? AND a.status='in_progress')
                    FROM quizzes q JOIN subjects s ON s.subject_id=q.subject_id JOIN users u ON u.user_id=q.teacher_id
                    WHERE q.status='published' AND q.archived_at IS NULL AND s.archived_at IS NULL
                        AND EXISTS(SELECT 1 FROM questions x WHERE x.quiz_id=q.quiz_id)
                    ORDER BY q.updated_at DESC,q.quiz_id DESC
                    """)) {
                statement.setQueryTimeout(10);
                statement.setLong(1,user.id()); statement.setLong(2,user.id());
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) quizzes.add(new Quiz(rows.getLong(1),rows.getString(2),rows.getString(3),
                            rows.getString(4),rows.getString(5),rows.getInt(6),rows.getInt(7),rows.getLong(8),rows.getLong(9),rows.getBoolean(10)));
                }
            }
            connection.commit();
            return List.copyOf(quizzes);
        }
    }
}
