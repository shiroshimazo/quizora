/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.DAO;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import quizora.auth.PasswordRecoveryService;
import quizora.auth.PasswordRecoveryService.Reason;
import quizora.auth.PasswordRecoveryService.RecoveryException;
import quizora.auth.PasswordRecoveryService.Ticket;
import quizora.database.databaseConnection;

/**
 * Stores password reset codes. Each account has at most one current code, saved only as a hash.
 * A new request replaces the old code.
 */
public final class PasswordRecoveryDAO {

    /** A code that was created but not yet emailed. */
    public record Pending(String id, String email, String code) { }

    /** The saved state of one reset code. */
    private record State(long userId, byte[] codeHash, int attempts, boolean verified) { }

    private static final long RESEND_WAIT_MILLIS = 30_000;
    private static final int MAX_ATTEMPTS = 5;

    /** Creates a new code for an active account, replacing any older code. */
    public Pending reserve(String email, Instant now, Supplier<String> codes) throws SQLException, RecoveryException {
        try (Connection connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            long userId;
            String storedEmail;
            String findAccount = "SELECT user_id, email FROM users WHERE email = ? AND is_active = 1 AND archived_at IS NULL";
            try (PreparedStatement statement = connection.prepareStatement(findAccount)) {
                statement.setString(1, email);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new RecoveryException(Reason.UNKNOWN_EMAIL, "No account found with this email. Would you like to sign up?");
                    }
                    userId = result.getLong("user_id");
                    storedEmail = result.getString("email");
                }
            }

            String code = codes.get();
            String findOldCode = "SELECT challenge_id, code_hash, requested_at FROM password_recovery WHERE user_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(findOldCode)) {
                statement.setLong(1, userId);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        if (now.toEpochMilli() - result.getLong("requested_at") < RESEND_WAIT_MILLIS) {
                            throw new RecoveryException(Reason.COOLDOWN, "Please wait 30 seconds before requesting another code.");
                        }
                        // Make sure a resend never repeats the previous six digits.
                        byte[] oldHash = result.getBytes("code_hash");
                        String oldId = result.getString("challenge_id");
                        while (MessageDigest.isEqual(oldHash, PasswordRecoveryService.digest(oldId, code))) {
                            code = codes.get();
                        }
                    }
                }
            }

            String id = UUID.randomUUID().toString();
            String saveCode = "INSERT INTO password_recovery (user_id, challenge_id, code_hash, requested_at) "
                    + "VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (user_id) DO UPDATE SET challenge_id = excluded.challenge_id, "
                    + "code_hash = excluded.code_hash, requested_at = excluded.requested_at, "
                    + "sent_at = NULL, expires_at = NULL, attempts = 0, verified = 0";
            try (PreparedStatement statement = connection.prepareStatement(saveCode)) {
                statement.setLong(1, userId);
                statement.setString(2, id);
                statement.setBytes(3, PasswordRecoveryService.digest(id, code));
                statement.setLong(4, now.toEpochMilli());
                statement.executeUpdate();
            }
            connection.commit();
            return new Pending(id, storedEmail, code);
        }
    }

    /** Records when the email was sent; the code expires five minutes after that. */
    public void activate(Ticket ticket) throws SQLException, RecoveryException {
        String sql = "UPDATE password_recovery SET sent_at = ?, expires_at = ? WHERE challenge_id = ?";
        try (Connection connection = databaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ticket.sentAt().toEpochMilli());
            statement.setLong(2, ticket.expiresAt().toEpochMilli());
            statement.setString(3, ticket.id());
            if (statement.executeUpdate() != 1) {
                throw superseded();
            }
        }
    }

    /** Checks the six-digit code; wrong guesses are counted. */
    public void verify(Ticket ticket, String code, Clock clock) throws SQLException, RecoveryException {
        try (Connection connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            State state = currentState(connection, ticket, clock);
            if (state.verified()) {
                throw new RecoveryException(Reason.SUPERSEDED, "This code has already been verified. Please request a new one.");
            }
            boolean correct = code != null && code.matches("[0-9]{6}")
                    && MessageDigest.isEqual(state.codeHash(), PasswordRecoveryService.digest(ticket.id(), code));
            if (!correct) {
                update(connection, "UPDATE password_recovery SET attempts = attempts + 1 WHERE challenge_id = ?", ticket.id());
                connection.commit();
                if (state.attempts() + 1 >= MAX_ATTEMPTS) {
                    throw tooManyAttempts();
                }
                throw new RecoveryException(Reason.INCORRECT, PasswordRecoveryService.INCORRECT);
            }
            update(connection, "UPDATE password_recovery SET verified = 1 WHERE challenge_id = ?", ticket.id());
            connection.commit();
        }
    }

    /** Saves the new password and uses up the code so it cannot be reused. */
    public void reset(Ticket ticket, String passwordHash, Clock clock) throws SQLException, RecoveryException {
        try (Connection connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            State state = currentState(connection, ticket, clock);
            if (!state.verified()) {
                throw new RecoveryException(Reason.INCORRECT, "Verify your code before resetting your password.");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users SET password_hash = ? WHERE user_id = ?")) {
                statement.setString(1, passwordHash);
                statement.setLong(2, state.userId());
                statement.executeUpdate();
            }
            update(connection, "DELETE FROM password_recovery WHERE challenge_id = ?", ticket.id());
            connection.commit();
        }
    }

    /** Loads the code's state, rejecting replaced, expired or locked codes and disabled accounts. */
    private State currentState(Connection connection, Ticket ticket, Clock clock) throws SQLException, RecoveryException {
        if (ticket == null) {
            throw superseded();
        }
        String sql = "SELECT r.user_id, r.code_hash, r.expires_at, r.attempts, r.verified, "
                + "u.email, u.is_active, u.archived_at "
                + "FROM password_recovery r JOIN users u ON u.user_id = r.user_id "
                + "WHERE r.challenge_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ticket.id());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || !result.getString("email").equalsIgnoreCase(ticket.email())
                        || !result.getBoolean("is_active")
                        || result.getString("archived_at") != null) {
                    throw superseded();
                }
                long expiresAt = result.getLong("expires_at");
                if (result.wasNull() || clock.millis() >= expiresAt) {
                    throw new RecoveryException(Reason.EXPIRED, PasswordRecoveryService.EXPIRED);
                }
                int attempts = result.getInt("attempts");
                if (attempts >= MAX_ATTEMPTS) {
                    throw tooManyAttempts();
                }
                return new State(result.getLong("user_id"), result.getBytes("code_hash"), attempts, result.getBoolean("verified"));
            }
        }
    }

    private static void update(Connection connection, String sql, String challengeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, challengeId);
            statement.executeUpdate();
        }
    }

    private static RecoveryException superseded() {
        return new RecoveryException(Reason.SUPERSEDED, "This code is no longer valid. Please request a new one.");
    }

    private static RecoveryException tooManyAttempts() {
        return new RecoveryException(Reason.ATTEMPTS, "Too many incorrect attempts. Please request a new code.");
    }
}
