/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.DAO;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.AdminProfile;
import quizora.model.ProfileChanges;
import quizora.model.ProfilePicture;

/**
 * Reads and updates the signed-in user's own profile. Teacher and student profiles extend this class
 * and only change the role check.
 */
public class AdminProfileDAO {
    private final userDAO.Connections connections;

    public AdminProfileDAO() {
        this(databaseConnection::getConnection);
    }

    protected AdminProfileDAO(userDAO.Connections connections) {
        this.connections = connections;
    }

    /** Subclasses replace this with their own role check. */
    protected void requireAccess(Connection connection, AuthenticatedUser user) throws SQLException {
        AccessCheck.requireAdmin(connection, user);
    }

    public AdminProfile load(AuthenticatedUser user) throws SQLException {
        try (Connection connection = connections.open()) {
            requireAccess(connection, user);
            return readProfile(connection, user.id());
        }
    }

    public AdminProfile save(AuthenticatedUser user, AdminProfile original, ProfileChanges changes) throws SQLException {
        if (changes == null) {
            throw new NullPointerException("Profile changes are required.");
        }
        checkOwnProfile(user, original);
        String sql = "UPDATE users SET full_name = ?, username = ?, email = ?, contact_number = ? WHERE user_id = ?";
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                checkUnchanged(connection, user, original);
                checkNotTaken(connection, user.id(), changes);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, changes.name());
                    statement.setString(2, changes.username());
                    statement.setString(3, changes.email());
                    statement.setString(4, changes.contact());
                    statement.setLong(5, user.id());
                    statement.executeUpdate();
                }
                AdminProfile updated = readProfile(connection, user.id());
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    /** Saves a PNG or JPEG picture after checking its type, size and dimensions. */
    public AdminProfile picture(AuthenticatedUser user, AdminProfile original, byte[] imageBytes)
            throws SQLException, IOException {
        byte[] picture = imageBytes.clone();
        ProfilePicture.validate(picture);
        checkOwnProfile(user, original);
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                checkUnchanged(connection, user, original);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users SET profile_picture = ? WHERE user_id = ?")) {
                    statement.setBytes(1, picture);
                    statement.setLong(2, user.id());
                    statement.executeUpdate();
                }
                AdminProfile updated = readProfile(connection, user.id());
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private static void checkOwnProfile(AuthenticatedUser user, AdminProfile original) {
        if (user == null || original == null || user.id() != original.id()) {
            throw new SecurityException("Only your own profile can be updated.");
        }
    }

    /** Rejects the save if the profile changed since it was shown on screen. */
    private void checkUnchanged(Connection connection, AuthenticatedUser user, AdminProfile original) throws SQLException {
        requireAccess(connection, user);
        AdminProfile current = readProfile(connection, user.id());
        if (!current.equals(original)) {
            throw new SQLException("Profile changed. Refresh and try again.", "40001");
        }
    }

    private static void checkNotTaken(Connection connection, long ownId, ProfileChanges changes) throws SQLException {
        String sql = "SELECT user_id FROM users WHERE user_id <> ? AND (username IN (?, ?) OR email IN (?, ?))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownId);
            statement.setString(2, changes.username());
            statement.setString(3, changes.email());
            statement.setString(4, changes.username());
            statement.setString(5, changes.email());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new IllegalArgumentException("That username or email is already in use.");
                }
            }
        }
    }

    private static AdminProfile readProfile(Connection connection, long userId) throws SQLException {
        String sql = "SELECT user_id, full_name, username, email, contact_number, profile_picture, created_at "
                + "FROM users WHERE user_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SecurityException("Account unavailable.");
                }
                String contact = result.getString("contact_number");
                byte[] pictureBytes = result.getBytes("profile_picture");
                // The picture is stored as text so two profiles can be compared with equals().
                String picture = "";
                if (pictureBytes != null) {
                    picture = Base64.getEncoder().encodeToString(pictureBytes);
                }
                return new AdminProfile(
                        result.getLong("user_id"),
                        result.getString("full_name"),
                        result.getString("username"),
                        result.getString("email"),
                        contact == null ? "" : contact,
                        picture,
                        result.getTimestamp("created_at").toLocalDateTime());
            }
        }
    }
}
