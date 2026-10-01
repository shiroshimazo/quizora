/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.dashboard;

import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import quizora.auth.UserSession;

/** Opens the signed-in user's role panel in the login window. */
public final class PanelRouter {
    private PanelRouter() { }

    public static void open(Stage stage) throws IOException {
        var user = UserSession.current();
        if (user == null) throw new IllegalStateException("Login is required");
        String view = switch (user.role()) {
            case "admin" -> "admin/adminDashboard";
            case "teacher" -> "teacher/teacherDashboard";
            case "student" -> "student/studentDashbaord";
            default -> throw new IllegalStateException("Unsupported account role");
        };
        Parent root = FXMLLoader.load(PanelRouter.class.getResource("/Resources/fxml/" + view + ".fxml"));

        // Open at up to 1440 x 900, capped at 92% of the current screen; minimum 900 x 650 when it fits.
        var screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(),
                Math.max(1, stage.getWidth()), Math.max(1, stage.getHeight()));
        Rectangle2D bounds = (screens.isEmpty() ? Screen.getPrimary() : screens.getFirst()).getVisualBounds();
        double width = Math.min(1440, bounds.getWidth() * 0.92);
        double height = Math.min(900, bounds.getHeight() * 0.92);
        stage.setMaximized(false);
        stage.setMinWidth(Math.min(900, width));
        stage.setMinHeight(Math.min(650, height));
        stage.setScene(new Scene(root, width, height));
        stage.setTitle("Quizora - " + user.role() + " - " + user.fullName());
        stage.setResizable(true);
        stage.show();
        // Stage dimensions include window decorations; keep those inside the work area too.
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(bounds.getMinX() + (bounds.getWidth() - width) / 2);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - height) / 2);
    }
}
