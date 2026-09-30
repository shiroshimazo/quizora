/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.controllerAuthentication;

import java.nio.file.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import javafx.application.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import quizora.auth.PasswordRecoveryService;
import quizora.DAO.PasswordRecoveryDAO;
import quizora.database.databaseConnection;

/** UI regression checks with fake time and delivery; never connects to Gmail. */
public final class PasswordRecoveryUiTest extends Application {
    private static final class MutableClock extends Clock {
        final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-09-29T12:02:15Z"));
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return time.get(); }
        void advance(long seconds) { time.updateAndGet(value -> value.plusSeconds(seconds)); }
    }
    private Parent root;
    private Stage stage;
    private Path directory;
    private final MutableClock clock = new MutableClock();
    private final AtomicReference<String> code = new AtomicReference<>();
    private final AtomicBoolean failDelivery = new AtomicBoolean();
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!fx(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) throw new AssertionError("UI condition timed out");
            Thread.sleep(30);
        }
    }
    private TextField field(String id) { return (TextField)root.lookup("#" + id); }
    private Button button(String id) { return (Button)root.lookup("#" + id); }
    private String label(String id) { return ((Label)root.lookup("#" + id)).getText(); }
    @Override public void start(Stage window) throws Exception {
        Platform.setImplicitExit(false); stage = window;
        directory = Files.createTempDirectory("quizora-recovery-ui-");
        System.setProperty("quizora.db", directory.resolve("test.db").toString());
        try (var connection = databaseConnection.getConnection(); var insert = connection.createStatement()) {
            insert.executeUpdate("INSERT INTO users(full_name,username,email,password_hash) VALUES ('Test','test','test@example.com','unused')");
        }
        quizora.Quizora.satoshi(14);
        var service = new PasswordRecoveryService(clock, (email, value) -> {
            if (failDelivery.get()) throw new java.io.IOException("Simulated failure"); code.set(value); return clock.instant();
        }, new PasswordRecoveryDAO());
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/Resources/fxml/authentication/forgotPassword.fxml"));
        loader.setControllerFactory(type -> new forgotPasswordController(service, clock));
        root = loader.load(); stage.setScene(new Scene(root,1000,500)); root.applyCss(); root.layout();
        new Thread(() -> {
            try {
                fx(() -> { field("emailField").setText("test@example.com"); button("continueButton").fire(); return null; });
                await(() -> root.lookup("#codeStep").isVisible() && !button("continueButton").isDisabled());
                fx(() -> {
                    check(label("sentAtLabel").startsWith("Code sent at "), "Sent timestamp");
                    check(label("expiresAtLabel").startsWith("Expires at "), "Expiry timestamp");
                    check(label("countdownLabel").equals("Time remaining: 5:00"), "Initial countdown");
                    capture(); return null;
                });
                clock.advance(299); await(() -> label("countdownLabel").equals("Time remaining: 0:01"));
                clock.advance(1); await(() -> button("continueButton").isDisabled());
                fx(() -> {
                    check(label("countdownLabel").equals("Time remaining: 0:00"), "Zero countdown");
                    check(label("recoveryStatus").equals(PasswordRecoveryService.EXPIRED), "Expired message");
                    check(button("resendButton").isVisible() && !button("resendButton").isDisabled(), "Resend available");
                    failDelivery.set(true); button("resendButton").fire(); return null;
                });
                await(() -> label("recoveryStatus").contains("couldn't send"));
                fx(() -> { check(button("continueButton").isDisabled(), "Cannot verify after delivery failure"); return null; });
                clock.advance(30); await(() -> !button("resendButton").isDisabled());
                fx(() -> { failDelivery.set(false); button("resendButton").fire(); return null; });
                await(() -> !button("continueButton").isDisabled());
                fx(() -> { check(label("countdownLabel").equals("Time remaining: 5:00"), "Resend restarts timer"); field("codeField").setText(code.get()); button("continueButton").fire(); return null; });
                await(() -> root.lookup("#passwordStep").isVisible());
                fx(() -> {
                    var loginLink = (Hyperlink)root.lookup("#loginLink"); loginLink.fire();
                    check(stage.getScene().getRoot() != root, "Return to login"); return null;
                });
                System.out.println("PASS: sent/expiry timestamps, live countdown, exact zero state, resend, failure, verification and navigation.");
                finish(0);
            } catch (Throwable failure) { failure.printStackTrace(); finish(1); }
        }, "recovery-ui-test").start();
    }
    private void capture() throws Exception {
        root.applyCss(); root.layout(); root.applyCss(); root.layout();
        var card=root.getChildrenUnmodifiable().getFirst();
        check(card.getBoundsInParent().getMinY()>=0 && card.getBoundsInParent().getMaxY()<=500, "Form fits window");
        var image=root.snapshot(null,null);
        var png=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(png,"png",Path.of(System.getProperty("java.io.tmpdir"),"quizora-recovery-timer.png").toFile());
    }
    private void finish(int exitCode) {
        try { Files.deleteIfExists(directory.resolve("test.db")); Files.deleteIfExists(directory.resolve("test.db-journal")); Files.deleteIfExists(directory); }
        catch(Exception failure) { failure.printStackTrace(); exitCode=1; }
        Platform.exit(); if(exitCode!=0)System.exit(exitCode);
    }
    public static void main(String[] args) { launch(args); }
}
