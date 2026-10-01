package quizora.DAO;

import java.sql.Connection;
import java.sql.SQLException;
import quizora.auth.AuthenticatedUser;

/** Shares profile editing while restricting access to the signed-in student. */
public final class StudentProfileDAO extends AdminProfileDAO {
    public StudentProfileDAO() { super(); }
    public StudentProfileDAO(userDAO.Connections connections) { super(connections); }
    @Override protected void requireAccess(Connection connection, AuthenticatedUser user, boolean lock) throws SQLException {
        requireStudent(connection,user);
    }
    static void requireStudent(Connection connection, AuthenticatedUser user) throws SQLException {
        if (user == null || !"student".equals(user.role())) throw new SecurityException("Student access is required.");
        try (var statement = connection.prepareStatement("SELECT 1 FROM users WHERE user_id=? AND role='student' AND is_active=1 AND archived_at IS NULL")) {
            statement.setLong(1,user.id()); statement.setQueryTimeout(10);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SecurityException("Active student access is required.");
            }
        }
    }
}
