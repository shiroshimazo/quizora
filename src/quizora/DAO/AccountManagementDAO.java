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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import quizora.auth.AuthenticatedUser;
import quizora.auth.PasswordHasher;
import quizora.database.databaseConnection;
import quizora.model.AccountChanges;
import quizora.model.AccountRecord;

/** Admin-only add, list, edit and archive for one account role (student or teacher). */
public class AccountManagementDAO {
    private final String role;

    protected AccountManagementDAO(String role) {
        if (!role.equals("student") && !role.equals("teacher")) {
            throw new IllegalArgumentException("Unsupported managed role.");
        }
        this.role = role;
    }

    public static void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("Password must contain 8 to 128 characters.");
        }
    }

    public List<AccountRecord> load(AuthenticatedUser admin) throws SQLException {
        List<AccountRecord> accounts = new ArrayList<>();
        String sql = "SELECT user_id, full_name, username, email, is_active, archived_at "
                + "FROM users WHERE role = ? ORDER BY user_id DESC";
        try (Connection connection = databaseConnection.getReadOnlyConnection()) {
            AccessCheck.requireAdmin(connection, admin);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, role);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        accounts.add(readAccount(result));
                    }
                }
            }
        }
        return accounts;
    }

    public AccountRecord create(AuthenticatedUser admin, AccountChanges details, String password) throws SQLException {
        if (details == null) {
            throw new IllegalArgumentException("Account details are required.");
        }
        validatePassword(password);
        char[] passwordCharacters = password.toCharArray();
        String passwordHash = PasswordHasher.hash(passwordCharacters);
        Arrays.fill(passwordCharacters, '\0');

        String sql = "INSERT INTO users (full_name, username, email, password_hash, role, is_active) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                AccessCheck.requireAdmin(connection, admin);
                checkNotTaken(connection, 0, details);
                long newId;
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, details.name());
                    statement.setString(2, details.username());
                    statement.setString(3, details.email());
                    statement.setString(4, passwordHash);
                    statement.setString(5, role);
                    statement.setBoolean(6, details.active());
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        newId = keys.getLong(1);
                    }
                }
                AccountRecord account = findAccount(connection, newId);
                connection.commit();
                return account;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    public AccountRecord edit(AuthenticatedUser admin, AccountRecord original, AccountChanges changes) throws SQLException {
        if (changes == null) {
            throw new IllegalArgumentException("Account changes are required.");
        }
        String sql = "UPDATE users SET full_name = ?, username = ?, email = ?, is_active = ? "
                + "WHERE user_id = ? AND role = ? AND archived_at IS NULL";
        try (Connection connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                checkCanChange(connection, admin, original);
                checkNotTaken(connection, original.id(), changes);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, changes.name());
                    statement.setString(2, changes.username());
                    statement.setString(3, changes.email());
                    statement.setBoolean(4, changes.active());
                    statement.setLong(5, original.id());
                    statement.setString(6, role);
                    statement.executeUpdate();
                }
                AccountRecord updated = findAccount(connection, original.id());
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    /** Archiving disables login but keeps the account and its quiz history. */
    public AccountRecord archive(AuthenticatedUser admin, AccountRecord original) throws SQLException {
        String sql = "UPDATE users SET is_active = 0, archived_at = datetime('now', 'localtime') "
                + "WHERE user_id = ? AND role = ? AND archived_at IS NULL";
        try (Connection connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                checkCanChange(connection, admin, original);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, original.id());
                    statement.setString(2, role);
                    statement.executeUpdate();
                }
                AccountRecord updated = findAccount(connection, original.id());
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    /** Rejects archived accounts and accounts that changed since the admin loaded them. */
    private void checkCanChange(Connection connection, AuthenticatedUser admin, AccountRecord original) throws SQLException {
        if (original == null || original.archived()) {
            throw new IllegalArgumentException("Archived accounts cannot be changed.");
        }
        AccessCheck.requireAdmin(connection, admin);
        AccountRecord current = findAccount(connection, original.id());
        if (!current.equals(original)) {
            throw new SQLException("This account changed since it was loaded. Refresh and try again.", "40001");
        }
    }

    /** Usernames and emails must be unique across every role, including archived accounts. */
    private void checkNotTaken(Connection connection, long ownId, AccountChanges details) throws SQLException {
        String sql = "SELECT user_id FROM users WHERE user_id <> ? AND (username IN (?, ?) OR email IN (?, ?))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownId);
            statement.setString(2, details.username());
            statement.setString(3, details.email());
            statement.setString(4, details.username());
            statement.setString(5, details.email());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new IllegalArgumentException("Username or email is already in use.");
                }
            }
        }
    }

    private AccountRecord findAccount(Connection connection, long id) throws SQLException {
        String sql = "SELECT user_id, full_name, username, email, is_active, archived_at "
                + "FROM users WHERE user_id = ? AND role = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, role);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Account no longer exists. Refresh and try again.", "40001");
                }
                return readAccount(result);
            }
        }
    }

    private static AccountRecord readAccount(ResultSet result) throws SQLException {
        return new AccountRecord(
                result.getLong("user_id"),
                result.getString("full_name"),
                result.getString("username"),
                result.getString("email"),
                result.getBoolean("is_active"),
                result.getString("archived_at") != null);
    }
}
