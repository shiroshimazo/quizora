package quizora.DAO;

import java.sql.SQLException;
import java.util.Optional;
import quizora.database.databaseConnection;

/** Database access only; password verification belongs to the authentication service. */
public class userDAO {
    public record LoginAccount(long id, String fullName, String role, String passwordHash, boolean active) { }

    public Optional<LoginAccount> findForLogin(String identifier) throws SQLException {
        try (var connection = databaseConnection.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT user_id, full_name, role, password_hash, "
                     + "(is_active AND archived_at IS NULL) AS is_active FROM users "
                     + "WHERE username = ? OR email = ? LIMIT 2")) {
            statement.setQueryTimeout(5);
            statement.setString(1, identifier);
            statement.setString(2, identifier);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                LoginAccount account = new LoginAccount(result.getLong("user_id"),
                        result.getString("full_name"), result.getString("role"),
                        result.getString("password_hash"), result.getBoolean("is_active"));
                // Fail closed if one user's username matches another user's email.
                return result.next() ? Optional.empty() : Optional.of(account);
            }
        }
    }
}
