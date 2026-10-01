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
import quizora.auth.AuthenticatedUser;

/** Checks that the signed-in user still has an active, non-archived account with the expected role. */
final class AccessCheck {

    private AccessCheck() { }

    static void requireAdmin(Connection connection, AuthenticatedUser user) throws SQLException {
        requireRole(connection, user, "admin", "Administrator access is required.");
    }

    static void requireTeacher(Connection connection, AuthenticatedUser user) throws SQLException {
        requireRole(connection, user, "teacher", "Teacher access is required.");
    }

    static void requireStudent(Connection connection, AuthenticatedUser user) throws SQLException {
        requireRole(connection, user, "student", "Student access is required.");
    }

    private static void requireRole(Connection connection, AuthenticatedUser user, String role, String message)
            throws SQLException {
        if (user == null || !role.equals(user.role())) {
            throw new SecurityException(message);
        }
        String sql = "SELECT user_id FROM users "
                + "WHERE user_id = ? AND role = ? AND is_active = 1 AND archived_at IS NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, user.id());
            statement.setString(2, role);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SecurityException(message);
                }
            }
        }
    }
}
