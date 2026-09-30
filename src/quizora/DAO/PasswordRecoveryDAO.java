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
import java.sql.SQLException;
import java.time.Instant;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import quizora.auth.PasswordRecoveryService;
import quizora.auth.PasswordRecoveryService.RecoveryException;
import quizora.auth.PasswordRecoveryService.Reason;
import quizora.auth.PasswordRecoveryService.Ticket;
import quizora.database.databaseConnection;

/** Atomic challenge replacement, verification and password updates in SQLite. */
public final class PasswordRecoveryDAO {
    public record Pending(String id, String email, String code) { }

    public Pending reserve(String email, Instant now, Supplier<String> codes) throws SQLException, RecoveryException {
        try (var connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            long userId;
            String storedEmail;
            try (var query = connection.prepareStatement(
                    "SELECT user_id, email FROM users WHERE email = ? AND is_active = 1 AND archived_at IS NULL")) {
                query.setString(1, email);
                try (var result = query.executeQuery()) {
                    if (!result.next()) throw new RecoveryException(Reason.UNKNOWN_EMAIL,
                            "No account found with this email. Would you like to sign up?");
                    userId = result.getLong(1); storedEmail = result.getString(2);
                }
            }
            String code = codes.get();
            try (var query = connection.prepareStatement("SELECT challenge_id, code_hash, requested_at FROM password_recovery WHERE user_id = ?")) {
                query.setLong(1, userId);
                try (var result = query.executeQuery()) {
                    if (result.next()) {
                        if (now.toEpochMilli() - result.getLong("requested_at") < 30_000)
                            throw new RecoveryException(Reason.COOLDOWN, "Please wait 30 seconds before requesting another code.");
                        // Ensure a resend cannot accidentally generate the previous six digits.
                        while (MessageDigest.isEqual(result.getBytes("code_hash"),
                                PasswordRecoveryService.digest(result.getString("challenge_id"), code))) code = codes.get();
                    }
                }
            }
            String id = UUID.randomUUID().toString();
            try (var save = connection.prepareStatement("INSERT INTO password_recovery "
                    + "(user_id, challenge_id, code_hash, requested_at) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT(user_id) DO UPDATE SET challenge_id=excluded.challenge_id, code_hash=excluded.code_hash, "
                    + "requested_at=excluded.requested_at, sent_at=NULL, expires_at=NULL, attempts=0, verified=0")) {
                save.setLong(1, userId); save.setString(2, id);
                save.setBytes(3, PasswordRecoveryService.digest(id, code)); save.setLong(4, now.toEpochMilli());
                save.executeUpdate();
            }
            connection.commit();
            return new Pending(id, storedEmail, code);
        }
    }

    public void activate(Ticket ticket) throws SQLException, RecoveryException {
        try (var connection = databaseConnection.getConnection();
             var update = connection.prepareStatement("UPDATE password_recovery SET sent_at=?, expires_at=? WHERE challenge_id=?")) {
            update.setLong(1, ticket.sentAt().toEpochMilli()); update.setLong(2, ticket.expiresAt().toEpochMilli());
            update.setString(3, ticket.id());
            if (update.executeUpdate() != 1) throw superseded();
        }
    }

    public void verify(Ticket ticket, String code, Clock clock) throws SQLException, RecoveryException {
        try (var connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            State state = current(connection, ticket, clock);
            if (state.verified()) throw new RecoveryException(Reason.SUPERSEDED, "This code has already been verified. Please request a new one.");
            if (code == null || !code.matches("[0-9]{6}") || !MessageDigest.isEqual(state.hash(), PasswordRecoveryService.digest(ticket.id(), code))) {
                try (var update = connection.prepareStatement("UPDATE password_recovery SET attempts=attempts+1 WHERE challenge_id=?")) {
                    update.setString(1, ticket.id()); update.executeUpdate();
                }
                connection.commit();
                if (state.attempts() + 1 >= 5) throw attempts();
                throw new RecoveryException(Reason.INCORRECT, PasswordRecoveryService.INCORRECT);
            }
            try (var update = connection.prepareStatement("UPDATE password_recovery SET verified=1 WHERE challenge_id=?")) {
                update.setString(1, ticket.id()); update.executeUpdate();
            }
            connection.commit();
        }
    }

    public void reset(Ticket ticket, String passwordHash, Clock clock) throws SQLException, RecoveryException {
        try (var connection = databaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            State state = current(connection, ticket, clock);
            if (!state.verified()) throw new RecoveryException(Reason.INCORRECT, "Verify your code before resetting your password.");
            try (var update = connection.prepareStatement("UPDATE users SET password_hash=? WHERE user_id=?")) {
                update.setString(1, passwordHash); update.setLong(2, state.userId()); update.executeUpdate();
            }
            try (var delete = connection.prepareStatement("DELETE FROM password_recovery WHERE challenge_id=?")) {
                delete.setString(1, ticket.id()); delete.executeUpdate();
            }
            connection.commit();
        }
    }

    private record State(long userId, byte[] hash, int attempts, boolean verified) { }
    private State current(Connection connection, Ticket ticket, Clock clock) throws SQLException, RecoveryException {
        if (ticket == null) throw superseded();
        try (var query = connection.prepareStatement("SELECT r.*, u.email, u.is_active, u.archived_at FROM password_recovery r "
                + "JOIN users u ON u.user_id=r.user_id WHERE r.challenge_id=?")) {
            query.setString(1, ticket.id());
            try (var result = query.executeQuery()) {
                if (!result.next() || !result.getString("email").equalsIgnoreCase(ticket.email())
                        || !result.getBoolean("is_active") || result.getString("archived_at") != null) throw superseded();
                long expiry = result.getLong("expires_at");
                if (result.wasNull() || clock.millis() >= expiry)
                    throw new RecoveryException(Reason.EXPIRED, PasswordRecoveryService.EXPIRED);
                if (result.getInt("attempts") >= 5) throw attempts();
                return new State(result.getLong("user_id"), result.getBytes("code_hash"),
                        result.getInt("attempts"), result.getBoolean("verified"));
            }
        }
    }
    private RecoveryException superseded() {
        return new RecoveryException(Reason.SUPERSEDED, "This code is no longer valid. Please request a new one.");
    }
    private RecoveryException attempts() {
        return new RecoveryException(Reason.ATTEMPTS, "Too many incorrect attempts. Please request a new code.");
    }
}
