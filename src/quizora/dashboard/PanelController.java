package quizora.dashboard;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import quizora.Quizora;

/** Shared navigation behavior for the three presentation-only role shells. */
public class PanelController implements Initializable {
    /** Test hook: when non-null, replaces the modal dialog (true = confirm). */
    public static java.util.function.BooleanSupplier confirmOverride;
    @FXML private ToggleGroup navigationGroup;
    @FXML private StackPane workspacePane;
    @FXML private javafx.scene.Node dashboardContent;
    @FXML private javafx.scene.Node createQuizContent;
    @FXML private quizora.controllerTeacher.createQuizController createQuizContentController;
    @FXML private javafx.scene.Node studentContent;
    @FXML private quizora.controllerAdmin.studentManagementController studentContentController;
    @FXML private javafx.scene.Node teacherContent;
    @FXML private quizora.controllerAdmin.teacherManagementController teacherContentController;
    @FXML private javafx.scene.Node quizContent;
    @FXML private quizora.controllerAdmin.quizManagementController quizContentController;

    @FXML private javafx.scene.Node subjectContent;
    @FXML private quizora.controllerAdmin.subjectCategoryManagementController subjectContentController;

    @FXML private javafx.scene.Node reportsContent;
    @FXML private quizora.controllerAdmin.reportsController reportsContentController;

    @FXML private javafx.scene.Node resultsContent;
    @FXML private quizora.controllerAdmin.resultsController resultsContentController;

    @FXML private javafx.scene.Node accountContent;
    @FXML private quizora.controllerAdmin.accountManagementController accountContentController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        navigationGroup.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null && previous != null) {
                previous.setSelected(true);
            }
        });
    }

    @FXML
    private void navigate(ActionEvent event) {
        ToggleButton destination = (ToggleButton) event.getSource();
        destination.setSelected(true);
        workspacePane.setAccessibleText(destination.getText() + " workspace");
        if(createQuizContent != null){
            boolean create="createQuizButton".equals(destination.getId());
            createQuizContent.setVisible(create);createQuizContent.setManaged(create);
            if(create)createQuizContentController.open();
        }
        if (dashboardContent != null) {
            boolean dashboard = "dashboardButton".equals(destination.getId());
            dashboardContent.setVisible(dashboard);
            dashboardContent.setManaged(dashboard);
        }
        if (studentContent != null) {
            boolean students = "studentManagementButton".equals(destination.getId());
            studentContent.setVisible(students);
            studentContent.setManaged(students);
            if (students) studentContentController.refresh();
        }
        if (accountContent != null) {
            boolean account = "accountManagementButton".equals(destination.getId());
            accountContent.setVisible(account);
            accountContent.setManaged(account);
            if (account) accountContentController.refresh();
        }
        if (resultsContent != null) {
            boolean results = "resultsButton".equals(destination.getId());
            resultsContent.setVisible(results);
            resultsContent.setManaged(results);
            if (results) resultsContentController.refresh();
        }
        if (reportsContent != null) {
            boolean reports = "reportsButton".equals(destination.getId());
            reportsContent.setVisible(reports);
            reportsContent.setManaged(reports);
            if (reports) reportsContentController.refresh();
        }
        if (subjectContent != null) {
            boolean subjects = "subjectCategoryManagementButton".equals(destination.getId());
            subjectContent.setVisible(subjects);
            subjectContent.setManaged(subjects);
            if (subjects) subjectContentController.refresh();
        }
        if (quizContent != null) {
            boolean quizzes = "quizManagementButton".equals(destination.getId());
            quizContent.setVisible(quizzes);
            quizContent.setManaged(quizzes);
            if (quizzes) quizContentController.refresh();
        }
        if (teacherContent != null) {
            boolean teachers = "teacherManagementButton".equals(destination.getId());
            teacherContent.setVisible(teachers);
            teacherContent.setManaged(teachers);
            if (teachers) teacherContentController.refresh();
        }
    }

    @FXML
    private void logout(ActionEvent event) throws IOException {
        if (!confirmLogout()) {
            return;
        }
        quizora.auth.UserSession.clear();
        new Quizora().start((Stage) workspacePane.getScene().getWindow());
    }

    private boolean confirmLogout() {
        if (confirmOverride != null) {
            return confirmOverride.getAsBoolean();
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Logout");
        alert.setHeaderText("Are you sure you want to log out?");
        alert.setContentText("You will be returned to the login screen.");
        alert.initOwner(workspacePane.getScene().getWindow());
        alert.initModality(Modality.APPLICATION_MODAL);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
}
