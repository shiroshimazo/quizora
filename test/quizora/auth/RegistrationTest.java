package quizora.auth;

import java.nio.file.*;
import java.sql.*;
import quizora.DAO.userDAO;

public class RegistrationTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void rejected(RegistrationService service, String name, String username, String email,
            String password, String confirm) throws Exception {
        char[] secret = password.toCharArray();
        char[] confirmation = confirm.toCharArray();
        try {
            service.register(name, username, email, secret, confirmation);
            throw new AssertionError("Invalid registration accepted");
        } catch (IllegalArgumentException expected) {
            for (char c : secret) check(c == 0, "Password cleared after rejection");
            for (char c : confirmation) check(c == 0, "Confirmation cleared after rejection");
        }
    }
    public static void main(String[] args) throws Exception {
        Path database = Files.createTempFile("quizora-registration-", ".db");
        String url = "jdbc:sqlite:" + database;
        try {
            String schema = Files.readString(Path.of("src/quizora/database/schema.sql"));
            String usersTable = schema.substring(schema.indexOf("CREATE TABLE IF NOT EXISTS users"));
            usersTable = usersTable.substring(0, usersTable.indexOf(';') + 1);
            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
                statement.execute(usersTable);
            }
            userDAO dao = new userDAO(() -> DriverManager.getConnection(url));
            RegistrationService service = new RegistrationService(dao);
            rejected(service, " ", "alex", "alex@example.com", "password123", "password123");
            rejected(service, "Alex", "has space", "alex@example.com", "password123", "password123");
            rejected(service, "Alex", "alex", "invalid", "password123", "password123");
            rejected(service, "Alex", "alex", "alex@example.com", "short", "short");
            rejected(service, "Alex", "alex", "alex@example.com", "password123", "different");
            char[] secret = "password123".toCharArray();
            service.register(" Alex ", " alex ", " alex@example.com ", secret, "password123".toCharArray());
            for (char c : secret) check(c == 0, "Password cleared after success");
            var account = dao.findForLogin("alex@example.com").orElseThrow();
            check(account.role().equals("student") && account.active(), "Active student only");
            check(account.fullName().equals("Alex"), "Whitespace stripped");
            check(PasswordHasher.verify("password123".toCharArray(), account.passwordHash()), "Login-compatible hash");
            check(!account.passwordHash().equals("password123"), "No plaintext password");
            check(dao.findForLogin("alex").isPresent(), "Username login");
            rejected(service, "Other", "ALEX", "other@example.com", "password123", "password123");
            rejected(service, "Other", "other", "ALEX@example.com", "password123", "password123");
            rejected(service, "Other", "alex@example.com", "other@example.com", "password123", "password123");
            service.register("Legacy", "legacy@example.com", "legacy-contact@example.com", "password123".toCharArray(), "password123".toCharArray());
            rejected(service, "Other", "other", "legacy@example.com", "password123", "password123");
            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM users")) {
                check(rows.next() && rows.getInt(1) == 2, "Rejected registrations do not insert users");
            }
            System.out.println("Registration checks passed (validation, duplicates, cross-field collisions, hashes, student role).");
        } finally { Files.deleteIfExists(database); }
    }
}
