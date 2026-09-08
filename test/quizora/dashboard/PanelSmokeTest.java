package quizora.dashboard;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import quizora.Quizora;

/** Run with JavaFX modules enabled; does not require database credentials. */
public class PanelSmokeTest {
    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                Quizora.satoshi(14);
                String[] views = {"admin/adminDashboard", "teacher/teacherDashboard",
                    "student/studentDashbaord"};
                int[] counts = {8, 6, 5};
                for (int i = 0; i < views.length; i++) {
                    Parent root = FXMLLoader.load(PanelSmokeTest.class.getResource(
                            "/Resources/fxml/" + views[i] + ".fxml"));
                    Stage stage = new Stage();
                    stage.setScene(new Scene(root, 1180, 700));
                    root.applyCss();
                    root.layout();
                    StackPane workspace = (StackPane) root.lookup("#workspacePane");
                    var buttons = root.lookupAll(".navigation-button");
                    if (buttons.size() != counts[i]) throw new AssertionError("Menu count");
                    for (var node : buttons) {
                        ToggleButton button = (ToggleButton) node;
                        button.fire();
                        button.fire();
                        if (!button.isSelected()) throw new AssertionError("Selection lost");
                        if (!workspace.getChildren().isEmpty()) throw new AssertionError("Not blank");
                        if (button.getBoundsInParent().getMaxY() > 550)
                            throw new AssertionError("Menu overflow");
                    }
                    ((Button) root.lookup("#logoutButton")).fire();
                    if (stage.getScene().lookup("#usernameField") == null)
                        throw new AssertionError("Logout did not open login");
                    stage.close();
                }
                System.out.println("PASS: three role layouts, 19 navigation destinations, blank workspaces, logout.");
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
