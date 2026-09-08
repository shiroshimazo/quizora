package quizora.auth;

import java.util.UUID;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import quizora.Quizora;
import quizora.dashboard.PanelRouter;
import quizora.database.databaseConnection;

/** Integration check using the local demo accounts and actual asynchronous FXML login. */
public final class LoginSmokeTest {
    private static Stage stage;

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws Exception {
        String hash = PasswordHasher.hash("test password".toCharArray());
        require(PasswordHasher.verify("test password".toCharArray(), hash), "Hash verification");
        require(!PasswordHasher.verify("wrong".toCharArray(), hash), "Wrong password");
        require(!PasswordHasher.verify("test password".toCharArray(), "plaintext"), "Plaintext rejected");
        AuthenticationService auth = new AuthenticationService();
        require(auth.authenticate("' OR 1=1 --", "anything".toCharArray()).isEmpty(), "SQL injection rejected");
        require(auth.authenticate("admin", "wrong".toCharArray()).isEmpty(), "Invalid login rejected");
        require(auth.authenticate("", new char[0]).isEmpty(), "Empty credentials rejected");

        // A dedicated inactive fixture is removed by its unique identity in finally.
        String fixture = UUID.randomUUID().toString();
        try (var connection = databaseConnection.getConnection()) {
            try (var insert = connection.prepareStatement("INSERT INTO users "
                    + "(full_name, username, email, password_hash, role, is_active) "
                    + "VALUES ('Inactive test', ?, ?, ?, 'student', FALSE)")) {
                insert.setString(1, fixture);
                insert.setString(2, fixture + "@example.invalid");
                insert.setString(3, hash);
                insert.executeUpdate();
            }
            try {
                require(auth.authenticate(fixture, "test password".toCharArray()).isEmpty(),
                        "Inactive account rejected");
            } finally {
                try (var delete = connection.prepareStatement("DELETE FROM users WHERE username=?")) {
                    delete.setString(1, fixture);
                    delete.executeUpdate();
                }
            }
        }

        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            fx(() -> {
                stage = new Stage();
                UserSession.clear();
                try {
                    PanelRouter.open(stage);
                    throw new AssertionError("Unauthenticated routing allowed");
                } catch (IllegalStateException expected) { }
                new Quizora().start(stage);
                return null;
            });
            for (String[] account : new String[][] {
                    {"admin", "Admin@123", "8"},
                    {"teacher", "Teacher@123", "6"},
                    {"student", "Student@123", "5"}}) {
                require(auth.authenticate(account[0] + "@quizora.local",
                        account[1].toCharArray()).orElseThrow().role().equals(account[0]), "Email login");
                fx(() -> {
                    var root = stage.getScene().getRoot();
                    ((TextField) root.lookup("#usernameField")).setText(account[0]);
                    ((PasswordField) root.lookup("#passwordField")).setText(account[1]);
                    CheckBox show = (CheckBox) root.lookup("#showPasswordCheckBox");
                    show.setSelected(true);
                    require(((TextField) root.lookup("#visiblePasswordField")).getText()
                            .equals(account[1]), "Show password binding");
                    show.setSelected(false);
                    ((Button) root.lookup("#loginButton")).fire();
                    require(root.lookup("#loginButton").isDisabled(), "Busy state");
                    return null;
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (!fx(() -> stage.getScene().lookup("#workspacePane") != null
                        && (!account[0].equals("admin") || ((Label) stage.getScene().lookup("#statusLabel"))
                                .getText().startsWith("Updated ")))) {
                    if (System.nanoTime() > deadline) {
                        throw new AssertionError("Login timed out: " + fx(() ->
                                ((Label) stage.getScene().lookup("#loginStatusLabel")).getText()));
                    }
                    Thread.sleep(50);
                }
                fx(() -> {
                    require(UserSession.current().role().equals(account[0]), "Session role");
                    var scene = stage.getScene();
                    require(scene.getRoot().lookupAll(".navigation-button").size()
                            == Integer.parseInt(account[2]), "Role-specific menu");
                    require(((StackPane) scene.lookup("#workspacePane")).getChildren().isEmpty()
                            != account[0].equals("admin"), "Only admin has dashboard content");
                    require(stage.isResizable(), "Role panels must be resizable");
                    var screen = javafx.stage.Screen.getScreensForRectangle(stage.getX(), stage.getY(),
                            stage.getWidth(), stage.getHeight()).getFirst().getVisualBounds();
                    require(stage.getX() >= screen.getMinX() && stage.getY() >= screen.getMinY()
                            && stage.getX()+stage.getWidth() <= screen.getMaxX()+1
                            && stage.getY()+stage.getHeight() <= screen.getMaxY()+1, "Panel fits screen");
                    quizora.dashboard.PanelController.confirmOverride = () -> false;
                    ((Button) scene.lookup("#logoutButton")).fire();
                    require(UserSession.current() != null, "Cancelled logout keeps session");
                    require(stage.getScene().lookup("#workspacePane") != null,
                            "Cancelled logout stays on panel");
                    quizora.dashboard.PanelController.confirmOverride = () -> true;
                    ((Button) scene.lookup("#logoutButton")).fire();
                    require(UserSession.current() == null, "Logout clears session");
                    require(stage.getScene().lookup("#usernameField") != null, "Logout returns to login");
                    require(!stage.isResizable() && stage.getMinWidth() == 0 && stage.getMinHeight() == 0,
                            "Logout restores login sizing");
                    require(((PasswordField) stage.getScene().lookup("#passwordField")).getText().isEmpty(),
                            "Logout clears password");
                    return null;
                });
            }
            System.out.println("PASS: hashes, invalid/inactive/injection rejection, username/email login, "
                    + "all three real UI routes, live admin dashboard, blank teacher/student panels and logout.");
        } finally {
            try {
                fx(() -> { quizora.dashboard.PanelController.confirmOverride = null;
                    if (stage != null) stage.close(); return null; });
            } catch (Exception ignored) { }
            Platform.exit();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
