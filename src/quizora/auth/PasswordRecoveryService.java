/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import quizora.DAO.PasswordRecoveryDAO;

/** Service checks remain authoritative even if the countdown or UI is bypassed. */
public final class PasswordRecoveryService {
    public static final long VALIDITY_SECONDS = 300;
    public static final String EXPIRED = "Code expired. Please request a new one.";
    public static final String INCORRECT = "The code you entered is incorrect.";
    public enum Reason { UNKNOWN_EMAIL, INCORRECT, EXPIRED, SUPERSEDED, ATTEMPTS, COOLDOWN, DELIVERY, STORAGE, PASSWORD }
    public static final class RecoveryException extends Exception {
        private final Reason reason;
        public RecoveryException(Reason reason, String message) { super(message); this.reason = reason; }
        public RecoveryException(Reason reason, String message, Throwable cause) { super(message, cause); this.reason = reason; }
        public Reason reason() { return reason; }
    }
    @FunctionalInterface public interface CodeSender { Instant send(String email, String code) throws Exception; }
    public record Ticket(String id, String email, Instant sentAt, Instant expiresAt) { }
    private final Clock clock;
    private final CodeSender sender;
    private final PasswordRecoveryDAO accounts;
    private final SecureRandom random = new SecureRandom();

    public PasswordRecoveryService() { this(Clock.systemUTC(), new GmailCodeSender(), new PasswordRecoveryDAO()); }
    public PasswordRecoveryService(Clock clock, CodeSender sender, PasswordRecoveryDAO accounts) {
        this.clock = clock; this.sender = sender; this.accounts = accounts;
    }

    public Ticket sendCode(String email) throws RecoveryException {
        email = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new RecoveryException(Reason.UNKNOWN_EMAIL, "Enter a valid email address.");
        PasswordRecoveryDAO.Pending pending;
        try {
            pending = accounts.reserve(email, clock.instant(), () -> String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)));
        } catch (java.sql.SQLException failure) { throw storage(failure); }
        // reserve() invalidates the previous code before contacting SMTP.
        Instant sentAt;
        try {
            sentAt = sender.send(pending.email(), pending.code()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        } catch (RecoveryException failure) {
            throw failure;
        } catch (jakarta.mail.AuthenticationFailedException failure) {
            throw new RecoveryException(Reason.DELIVERY,
                    "Gmail rejected the sender credentials. Check the Gmail address and app password in smtp.local.properties.", failure);
        } catch (Exception failure) {
            throw new RecoveryException(Reason.DELIVERY,
                    "We couldn't send your email. Please try again shortly or contact support.", failure);
        }
        Ticket ticket = new Ticket(pending.id(), pending.email(), sentAt, sentAt.plusSeconds(VALIDITY_SECONDS));
        try { accounts.activate(ticket); } catch (java.sql.SQLException failure) { throw storage(failure); }
        return ticket;
    }

    public void verify(Ticket ticket, String code) throws RecoveryException {
        try { accounts.verify(ticket, code, clock); }
        catch (java.sql.SQLException failure) { throw storage(failure); }
    }

    public void resetPassword(Ticket ticket, char[] password, char[] confirmation) throws RecoveryException {
        try {
            if (password.length < 8 || password.length > 128)
                throw new RecoveryException(Reason.PASSWORD, "Use a password between 8 and 128 characters.");
            if (!Arrays.equals(password, confirmation))
                throw new RecoveryException(Reason.PASSWORD, "Passwords do not match.");
            String hash = PasswordHasher.hash(password);
            accounts.reset(ticket, hash, clock);
        } catch (java.sql.SQLException failure) { throw storage(failure); }
        finally { Arrays.fill(password, '\0'); Arrays.fill(confirmation, '\0'); }
    }

    public static byte[] digest(String id, String code) {
        try { return MessageDigest.getInstance("SHA-256").digest((id + ":" + code).getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private RecoveryException storage(Exception failure) {
        return new RecoveryException(Reason.STORAGE, "Unable to access your account. Please try again.", failure);
    }
}
