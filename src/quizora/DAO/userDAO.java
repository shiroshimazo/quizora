/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.DAO;

import java.sql.SQLException;
import java.util.Optional;
import quizora.database.databaseConnection;

/** Database access only; password verification belongs to the authentication service. */
public class userDAO {
    @FunctionalInterface public interface Connections { java.sql.Connection open() throws SQLException; }
    private final Connections connections;
    public userDAO() { this(databaseConnection::getConnection); }
    public userDAO(Connections connections) { this.connections = connections; }

    /** A single write prevents two registrations from claiming the same login identifier. */
    public boolean createStudent(quizora.model.AccountChanges details, String hash) throws SQLException {
        try (var connection = connections.open(); var statement = connection.prepareStatement("""
                INSERT INTO users(full_name, username, email, password_hash, role, is_active)
                SELECT ?, ?, ?, ?, 'student', 1
                WHERE NOT EXISTS (SELECT 1 FROM users
                    WHERE username COLLATE NOCASE IN (?, ?) OR email COLLATE NOCASE IN (?, ?))
                """)) {
            statement.setQueryTimeout(5);
            statement.setString(1, details.name());
            statement.setString(2, details.username());
            statement.setString(3, details.email());
            statement.setString(4, hash);
            for (int i = 5; i <= 8; i++) statement.setString(i, i % 2 == 1 ? details.username() : details.email());
            return statement.executeUpdate() == 1;
        } catch (SQLException error) {
            if (databaseConnection.isDuplicateKey(error)) return false;
            throw error;
        }
    }
    public record LoginAccount(long id, String fullName, String role, String passwordHash, boolean active) { }

    public Optional<LoginAccount> findForLogin(String identifier) throws SQLException {
        try (var connection = connections.open();
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
