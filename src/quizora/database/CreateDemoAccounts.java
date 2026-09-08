package quizora.database;

import java.sql.SQLException;
import quizora.auth.PasswordHasher;

/** Explicit local development setup; never runs automatically during login. */
public final class CreateDemoAccounts {
    public static void main(String[] args) throws SQLException {
        String[][] accounts = {
            {"admin", "Demo Administrator", "Admin@123"},
            {"teacher", "Demo Teacher", "Teacher@123"},
            {"student", "Demo Student", "Student@123"}
        };
        try (var connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (String[] account : accounts) {
                    String email = account[0] + "@quizora.local";
                    try (var check = connection.prepareStatement(
                            "SELECT user_id FROM users WHERE username IN (?, ?) OR email IN (?, ?)")) {
                        check.setString(1, account[0]);
                        check.setString(2, email);
                        check.setString(3, account[0]);
                        check.setString(4, email);
                        try (var existing = check.executeQuery()) {
                            if (existing.next()) {
                                System.out.println("Skipped existing identity: " + account[0]);
                                continue;
                            }
                        }
                    }
                    try (var insert = connection.prepareStatement(
                            "INSERT INTO users (full_name, username, email, password_hash, role) "
                            + "VALUES (?, ?, ?, ?, ?)")) {
                        insert.setString(1, account[1]);
                        insert.setString(2, account[0]);
                        insert.setString(3, email);
                        insert.setString(4, PasswordHasher.hash(account[2].toCharArray()));
                        insert.setString(5, account[0]);
                        insert.executeUpdate();
                    }
                }
                connection.commit();
                System.out.println("Demo account setup complete. Existing accounts were not changed.");
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }
}
