package quizora.dashboard;

import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Shared sizing for authenticated panels and development previews. */
public final class PanelWindow {
    private PanelWindow() { }

    public static void show(Stage stage, Parent root, String title) {
        var screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(),
                Math.max(1, stage.getWidth()), Math.max(1, stage.getHeight()));
        Rectangle2D bounds = (screens.isEmpty() ? Screen.getPrimary() : screens.getFirst()).getVisualBounds();
        double width = Math.min(1440, bounds.getWidth() * 0.92);
        double height = Math.min(900, bounds.getHeight() * 0.92);
        stage.setMaximized(false);
        stage.setMinWidth(Math.min(900, width));
        stage.setMinHeight(Math.min(650, height));
        stage.setScene(new Scene(root, width, height));
        stage.setTitle(title);
        stage.setResizable(true);
        stage.show();
        // Stage dimensions include window decorations; keep those inside the work area too.
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(bounds.getMinX() + (bounds.getWidth() - width) / 2);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - height) / 2);
    }
}
