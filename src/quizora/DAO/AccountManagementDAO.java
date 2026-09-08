package quizora.DAO;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.AccountChanges;
import quizora.model.AccountRecord;

/** Shared admin-only reads, creation and non-destructive updates for a fixed account role. */
public class AccountManagementDAO {
    private final String role;

    protected AccountManagementDAO(String role) {
        if (!"student".equals(role) && !"teacher".equals(role))
            throw new IllegalArgumentException("Unsupported managed role.");
        this.role = role;
    }
    private static final String PROJECTION =
            "SELECT user_id,full_name,username,email,is_active,archived_at FROM users ";

    public AccountRecord create(AuthenticatedUser admin, AccountChanges details, String password) throws SQLException {
        if (details == null) throw new IllegalArgumentException("Account details are required.");
        validatePassword(password);
        try (var connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdmin(connection, admin, true);
                try (var duplicate = connection.prepareStatement(
                        "SELECT user_id FROM users WHERE username IN (?,?) OR email IN (?,?) FOR UPDATE")) {
                    duplicate.setQueryTimeout(10);
                    duplicate.setString(1, details.username());
                    duplicate.setString(2, details.email());
                    duplicate.setString(3, details.username());
                    duplicate.setString(4, details.email());
                    try (var result = duplicate.executeQuery()) {
                        if (result.next()) throw new SQLException("Username or email is already in use.", "23000", 1062);
                    }
                }
                char[] secret = password.toCharArray();
                String hash;
                try { hash = quizora.auth.PasswordHasher.hash(secret); }
                finally { java.util.Arrays.fill(secret, '\0'); }
                AccountRecord created;
                try (var statement = connection.prepareStatement(
                        "INSERT INTO users(full_name,username,email,password_hash,role,is_active) VALUES(?,?,?,?,'" + role + "',?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    statement.setQueryTimeout(10);
                    statement.setString(1, details.name());
                    statement.setString(2, details.username());
                    statement.setString(3, details.email());
                    statement.setString(4, hash);
                    statement.setBoolean(5, details.active());
                    statement.executeUpdate();
                    try (var keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Account ID was not returned.");
                        created = findLocked(connection, keys.getLong(1));
                    }
                }
                connection.commit();
                return created;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    public static void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8 || password.length() > 128)
            throw new IllegalArgumentException("Password must contain 8 to 128 characters.");
    }

    public List<AccountRecord> load(AuthenticatedUser admin) throws SQLException {
        try (var connection = databaseConnection.getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            requireAdmin(connection, admin, false);
            List<AccountRecord> records = new ArrayList<>();
            try (var statement = connection.prepareStatement(PROJECTION + "WHERE role='" + role + "' ORDER BY user_id DESC")) {
                statement.setQueryTimeout(10);
                try (var result = statement.executeQuery()) {
                    while (result.next()) records.add(read(result));
                }
            }
            connection.commit();
            return List.copyOf(records);
        }
    }

    public AccountRecord edit(AuthenticatedUser admin, AccountRecord original, AccountChanges changes) throws SQLException {
        if (changes == null) throw new IllegalArgumentException("Account changes are required.");
        return change(admin, original, changes);
    }

    public AccountRecord archive(AuthenticatedUser admin, AccountRecord original) throws SQLException {
        return change(admin, original, null);
    }

    private AccountRecord change(AuthenticatedUser admin, AccountRecord original, AccountChanges changes) throws SQLException {
        if (original == null || original.archived()) throw new IllegalArgumentException("Archived accounts cannot be changed.");
        try (var connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdmin(connection, admin, true);
                AccountRecord current = findLocked(connection, original.id());
                if (!current.equals(original)) throw new SQLException(
                        "This account changed since it was loaded. Refresh and try again.", "40001");
                if (changes != null) {
                    try (var duplicate = connection.prepareStatement("""
                            SELECT user_id FROM users WHERE user_id<>?
                            AND (username IN (?,?) OR email IN (?,?)) FOR UPDATE
                            """)) {
                        duplicate.setQueryTimeout(10);
                        duplicate.setLong(1, original.id());
                        duplicate.setString(2, changes.username());
                        duplicate.setString(3, changes.email());
                        duplicate.setString(4, changes.username());
                        duplicate.setString(5, changes.email());
                        try (var result = duplicate.executeQuery()) {
                            if (result.next()) throw new SQLException("Username or email is already in use.", "23000", 1062);
                        }
                    }
                }
                String sql = changes == null
                        ? "UPDATE users SET is_active=FALSE,archived_at=CURRENT_TIMESTAMP WHERE user_id=? AND role='" + role + "' AND archived_at IS NULL"
                        : "UPDATE users SET full_name=?,username=?,email=?,is_active=? WHERE user_id=? AND role='" + role + "' AND archived_at IS NULL";
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setQueryTimeout(10);
                    if (changes == null) statement.setLong(1, original.id());
                    else {
                        statement.setString(1, changes.name());
                        statement.setString(2, changes.username());
                        statement.setString(3, changes.email());
                        statement.setBoolean(4, changes.active());
                        statement.setLong(5, original.id());
                    }
                    if (statement.executeUpdate() != 1)
                        throw new SQLException("Account is unavailable. Refresh and try again.", "40001");
                }
                AccountRecord updated = findLocked(connection, original.id());
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private AccountRecord findLocked(Connection connection, long id) throws SQLException {
        try (var statement = connection.prepareStatement(PROJECTION + "WHERE user_id=? AND role='" + role + "' FOR UPDATE")) {
            statement.setQueryTimeout(10);
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Account no longer exists. Refresh and try again.", "40001");
                return read(result);
            }
        }
    }

    static void requireAdmin(Connection connection, AuthenticatedUser admin, boolean lock) throws SQLException {
        if (admin == null || !"admin".equals(admin.role())) throw new SecurityException("Administrator access is required.");
        try (var statement = connection.prepareStatement("SELECT user_id FROM users "
                + "WHERE user_id=? AND role='admin' AND is_active=TRUE AND archived_at IS NULL" + (lock ? " FOR SHARE" : ""))) {
            statement.setQueryTimeout(10);
            statement.setLong(1, admin.id());
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new SecurityException("Administrator access is required.");
            }
        }
    }

    private static AccountRecord read(ResultSet result) throws SQLException {
        return new AccountRecord(result.getLong("user_id"), result.getString("full_name"),
                result.getString("username"), result.getString("email"), result.getBoolean("is_active"),
                result.getTimestamp("archived_at") != null);
    }
}
