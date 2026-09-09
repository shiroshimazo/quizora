package quizora.DAO;

import java.sql.*;
import java.util.Base64;
import java.util.Objects;
import quizora.auth.AuthenticatedUser;
import quizora.database.databaseConnection;
import quizora.model.*;

public final class AdminProfileDAO {
    public AdminProfile load(AuthenticatedUser admin) throws SQLException {
        try (var c = databaseConnection.getConnection()) {
            c.setReadOnly(true); c.setAutoCommit(false);
            AccountManagementDAO.requireAdmin(c, admin, false);
            var profile = read(c, admin.id(), false); c.commit(); return profile;
        }
    }
    public AdminProfile save(AuthenticatedUser admin, AdminProfile original, ProfileChanges changes) throws SQLException {
        Objects.requireNonNull(changes); return change(admin, original, changes, null);
    }
    public AdminProfile picture(AuthenticatedUser admin, AdminProfile original, byte[] bytes) throws SQLException, java.io.IOException {
        bytes = bytes.clone(); ProfilePicture.validate(bytes); return change(admin, original, null, bytes);
    }
    private AdminProfile change(AuthenticatedUser admin, AdminProfile original, ProfileChanges changes, byte[] picture) throws SQLException {
        if (admin == null || original == null || admin.id() != original.id()) throw new SecurityException("Only your own profile can be updated.");
        try (var c = databaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                AccountManagementDAO.requireAdmin(c, admin, true);
                var current = read(c, admin.id(), true);
                if (!current.equals(original)) throw new SQLException("Profile changed. Refresh and try again.", "40001");
                if (changes != null) {
                    try (var s = c.prepareStatement("SELECT user_id FROM users WHERE user_id<>? AND (username IN (?,?) OR email IN (?,?)) FOR UPDATE")) {
                        s.setQueryTimeout(10); s.setLong(1, admin.id()); s.setString(2, changes.username()); s.setString(3, changes.email()); s.setString(4, changes.username()); s.setString(5, changes.email());
                        try (var r = s.executeQuery()) { if (r.next()) throw new SQLException("Username or email already in use.", "23000", 1062); }
                    }
                }
                try (var s = c.prepareStatement(changes == null ? "UPDATE users SET profile_picture=? WHERE user_id=?" : "UPDATE users SET full_name=?,username=?,email=?,contact_number=? WHERE user_id=?")) {
                    s.setQueryTimeout(10);
                    if (changes == null) { s.setBytes(1, picture); s.setLong(2, admin.id()); }
                    else { s.setString(1, changes.name()); s.setString(2, changes.username()); s.setString(3, changes.email()); s.setString(4, changes.contact()); s.setLong(5, admin.id()); }
                    s.executeUpdate();
                }
                var updated = read(c, admin.id(), false); c.commit(); return updated;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
    private AdminProfile read(Connection c, long id, boolean lock) throws SQLException {
        try (var s = c.prepareStatement("SELECT user_id,full_name,username,email,contact_number,profile_picture,created_at FROM users WHERE user_id=?" + (lock ? " FOR UPDATE" : ""))) {
            s.setQueryTimeout(10); s.setLong(1, id);
            try (var r = s.executeQuery()) {
                if (!r.next()) throw new SecurityException("Account unavailable.");
                byte[] bytes = r.getBytes(6);
                return new AdminProfile(r.getLong(1), r.getString(2), r.getString(3), r.getString(4), Objects.requireNonNullElse(r.getString(5), ""), bytes == null ? "" : Base64.getEncoder().encodeToString(bytes), r.getTimestamp(7).toLocalDateTime());
            }
        }
    }
}
