package quizora.dashboard;

import java.io.IOException;
import java.util.Map;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import quizora.Quizora;

/** Development preview only; role selection does not authenticate a user. */
public class PanelPreview extends Application {
    private static final Map<String, String> VIEWS = Map.of(
            "admin", "/Resources/fxml/admin/adminDashboard.fxml",
            "teacher", "/Resources/fxml/teacher/teacherDashboard.fxml",
            "student", "/Resources/fxml/student/studentDashbaord.fxml");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        String role = getParameters().getRaw().isEmpty() ? "admin" : getParameters().getRaw().getFirst();
        String view = VIEWS.get(role);
        if (view == null) {
            throw new IllegalArgumentException("Preview role must be admin, teacher, or student");
        }
        Quizora.satoshi(14); // Register bundled font faces before loading CSS.
        stage.setScene(new Scene(FXMLLoader.load(PanelPreview.class.getResource(view)), 1180, 700));
        stage.setTitle("Quizora - " + role + " panel preview");
        stage.setResizable(false);
        stage.show();
    }
}

