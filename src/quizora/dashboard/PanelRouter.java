package quizora.dashboard;

import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import quizora.auth.UserSession;

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
        PanelWindow.show(stage, root, "Quizora - " + user.role() + " - " + user.fullName());
    }
}
