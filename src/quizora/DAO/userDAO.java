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
import java.util.Optional;
import quizora.database.databaseConnection;
import quizora.model.AccountChanges;

/** Login lookup and student self-registration. Password checking is done by AuthenticationService. */
public class userDAO {

    /** Where a DAO gets its connection from; tests pass in a temporary database. */
    @FunctionalInterface
    public interface Connections {
        Connection open() throws SQLException;
    }

    /** What login needs to know about an account. Never leaves the auth layer. */
    public record LoginAccount(long id, String fullName, String role, String passwordHash, boolean active) { }

    private final Connections connections;

    public userDAO() {
        this(databaseConnection::getConnection);
    }

    public userDAO(Connections connections) {
        this.connections = connections;
    }

    /**
     * Creates an active student account. Returns false if the username or email is already used by anyone.
     * The check and the insert are one statement, so two people cannot claim the same name at once.
     */
    public boolean createStudent(AccountChanges details, String passwordHash) throws SQLException {
        String sql = "INSERT INTO users (full_name, username, email, password_hash, role, is_active) "
                + "SELECT ?, ?, ?, ?, 'student', 1 "
                + "WHERE NOT EXISTS (SELECT 1 FROM users "
                + "WHERE username COLLATE NOCASE IN (?, ?) OR email COLLATE NOCASE IN (?, ?))";
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, details.name());
            statement.setString(2, details.username());
            statement.setString(3, details.email());
            statement.setString(4, passwordHash);
            statement.setString(5, details.username());
            statement.setString(6, details.email());
            statement.setString(7, details.username());
            statement.setString(8, details.email());
            return statement.executeUpdate() == 1;
        } catch (SQLException error) {
            if (databaseConnection.isDuplicateKey(error)) {
                return false;
            }
            throw error;
        }
    }

    /** Finds an account by username or email. Archived accounts are returned as inactive. */
    public Optional<LoginAccount> findForLogin(String usernameOrEmail) throws SQLException {
        String sql = "SELECT user_id, full_name, role, password_hash, "
                + "(is_active AND archived_at IS NULL) AS is_active "
                + "FROM users WHERE username = ? OR email = ? LIMIT 2";
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, usernameOrEmail);
            statement.setString(2, usernameOrEmail);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                LoginAccount account = new LoginAccount(
                        result.getLong("user_id"),
                        result.getString("full_name"),
                        result.getString("role"),
                        result.getString("password_hash"),
                        result.getBoolean("is_active"));
                // If one user's username matches another user's email, refuse rather than guess.
                if (result.next()) {
                    return Optional.empty();
                }
                return Optional.of(account);
            }
        }
    }
}
