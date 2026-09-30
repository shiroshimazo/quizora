/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.auth;

import java.nio.file.Files;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import quizora.DAO.PasswordRecoveryDAO;
import quizora.auth.PasswordRecoveryService.*;
import quizora.database.databaseConnection;

/** Standalone regression checks using a temporary SQLite database and no network. */
public final class PasswordRecoveryTest {
    private static int checks;
    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-29T12:02:15Z"));
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
        void advance(long millis) { now.updateAndGet(time -> time.plusMillis(millis)); }
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static void expect(Reason reason, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected " + reason); }
        catch (RecoveryException failure) { check(failure.reason() == reason, "Expected " + reason + ", got " + failure.reason()); }
    }
    private static String different(String code) { return code.equals("000000") ? "000001" : "000000"; }
    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("quizora-recovery-test-");
        var database = directory.resolve("test.db");
        System.setProperty("quizora.db", database.toString());
        try {
            try (var connection = databaseConnection.getConnection(); var insert = connection.prepareStatement(
                    "INSERT INTO users(full_name, username, email, password_hash, role) VALUES ('Test', 'test', 'test@example.com', ?, 'student')")) {
                insert.setString(1, PasswordHasher.hash("originalPassword".toCharArray())); insert.executeUpdate();
            }
            MutableClock clock = new MutableClock();
            AtomicReference<String> delivered = new AtomicReference<>();
            PasswordRecoveryDAO dao = new PasswordRecoveryDAO();
            var service = new PasswordRecoveryService(clock, (email, code) -> { check(email.equals("test@example.com"), "Canonical recipient"); delivered.set(code); return clock.instant(); }, dao);
            expect(Reason.UNKNOWN_EMAIL, () -> service.sendCode("unknown@example.com"));
            Ticket first = service.sendCode("TEST@example.com"); String firstCode = delivered.get();
            check(firstCode.matches("[0-9]{6}"), "Six ASCII digits");
            check(Duration.between(first.sentAt(), first.expiresAt()).equals(Duration.ofMinutes(5)), "Exactly five minutes");
            expect(Reason.COOLDOWN, () -> service.sendCode("test@example.com"));
            expect(Reason.INCORRECT, () -> service.verify(first, different(firstCode)));
            clock.advance(299_999); service.verify(first, firstCode);
            check(true, "Valid one millisecond before expiry");
            clock.advance(1);
            expect(Reason.EXPIRED, () -> service.resetPassword(first, "newPassword".toCharArray(), "newPassword".toCharArray()));
            Ticket boundary = service.sendCode("test@example.com"); String boundaryCode = delivered.get();
            clock.advance(300_000); expect(Reason.EXPIRED, () -> service.verify(boundary, boundaryCode));
            Ticket old = service.sendCode("test@example.com"); String oldCode = delivered.get();
            clock.advance(30_000); Ticket latest = service.sendCode("test@example.com"); String latestCode = delivered.get();
            check(!oldCode.equals(latestCode), "Resend uses different digits");
            expect(Reason.SUPERSEDED, () -> service.verify(old, oldCode));
            expect(Reason.INCORRECT, () -> service.verify(latest, oldCode));
            expect(Reason.INCORRECT, () -> service.resetPassword(latest, "newPassword".toCharArray(), "newPassword".toCharArray()));
            service.verify(latest, latestCode);
            expect(Reason.SUPERSEDED, () -> service.verify(latest, latestCode));
            expect(Reason.PASSWORD, () -> service.resetPassword(latest, "newPassword".toCharArray(), "differentPassword".toCharArray()));
            char[] password = "newPassword".toCharArray(), confirmation = "newPassword".toCharArray();
            service.resetPassword(latest, password, confirmation);
            check(password[0] == 0 && confirmation[0] == 0, "Password arrays erased");
            var account = new quizora.DAO.userDAO().findForLogin("test@example.com").orElseThrow();
            check(PasswordHasher.verify("newPassword".toCharArray(), account.passwordHash()), "New hash persisted");
            check(!PasswordHasher.verify("originalPassword".toCharArray(), account.passwordHash()), "Old password rejected");
            expect(Reason.SUPERSEDED, () -> service.resetPassword(latest, "anotherPassword".toCharArray(), "anotherPassword".toCharArray()));
            Ticket attempts = service.sendCode("test@example.com"); String wrong = different(delivered.get());
            for (int i=0;i<4;i++) expect(Reason.INCORRECT, () -> service.verify(attempts, wrong));
            expect(Reason.ATTEMPTS, () -> service.verify(attempts, wrong));
            expect(Reason.ATTEMPTS, () -> service.verify(attempts, delivered.get()));
            clock.advance(30_000); Ticket beforeFailure = service.sendCode("test@example.com"); String beforeFailureCode = delivered.get();
            clock.advance(30_000);
            var failing = new PasswordRecoveryService(clock, (email, code) -> { throw new java.io.IOException("Simulated SMTP failure"); }, dao);
            expect(Reason.DELIVERY, () -> failing.sendCode("test@example.com"));
            expect(Reason.SUPERSEDED, () -> service.verify(beforeFailure, beforeFailureCode));
            clock.advance(30_000);
            var delayed = new PasswordRecoveryService(clock, (email, code) -> { clock.advance(12_000); delivered.set(code); return clock.instant(); }, dao);
            Ticket afterDelay = delayed.sendCode("test@example.com");
            check(afterDelay.sentAt().equals(clock.instant()), "Clock starts after SMTP acceptance");
            check(afterDelay.expiresAt().equals(clock.instant().plusSeconds(300)), "Delivery time does not reduce validity");
            clock.advance(30_000);
            CountDownLatch sending = new CountDownLatch(1), release = new CountDownLatch(1);
            var blocked = new PasswordRecoveryService(clock, (email, code) -> { sending.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timeout"); return clock.instant(); }, dao);
            var executor = Executors.newSingleThreadExecutor();
            try {
                Future<Ticket> stale = executor.submit(() -> blocked.sendCode("test@example.com"));
                check(sending.await(10, TimeUnit.SECONDS), "First send reserved");
                clock.advance(31_000); Ticket replacement = service.sendCode("test@example.com");
                release.countDown();
                try { stale.get(10, TimeUnit.SECONDS); throw new AssertionError("Old completion must not overwrite latest"); }
                catch (ExecutionException failure) { check(((RecoveryException)failure.getCause()).reason() == Reason.SUPERSEDED, "Late completion rejected"); }
                service.verify(replacement, delivered.get());
            } finally { release.countDown(); executor.shutdownNow(); }
            System.out.println("PASS: " + checks + " recovery assertions; temporary database only; no email sent.");
        } finally {
            Files.deleteIfExists(database); Files.deleteIfExists(directory.resolve("test.db-journal")); Files.deleteIfExists(directory);
        }
    }
}
