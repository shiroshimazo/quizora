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
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import quizora.Quizora;
import quizora.auth.UserSession;
import quizora.controllerAdmin.*;
import quizora.controllerStudent.availableQuizzesController;
import quizora.controllerStudent.quizResultsController;
import quizora.controllerStudent.takeQuizController;
import quizora.controllerTeacher.assignedSubjectsController;
import quizora.controllerTeacher.createQuizController;
import quizora.controllerTeacher.quizStatisticsController;
import quizora.controllerTeacher.studentResultsController;

/** Sidebar navigation and logout shared by the admin, teacher and student shells. */
public class PanelController implements Initializable {
    @FXML private StackPane workspacePane;
    @FXML private ToggleButton takeQuizButton;

    // Each shell injects only its own pages; the others stay null and are skipped.
    @FXML private Node dashboardContent, studentContent, teacherContent, quizContent, subjectContent, reportsContent,
            resultsContent, accountContent, createQuizContent, assignedSubjectsContent, studentResultsContent,
            quizStatisticsContent, teacherProfileContent, availableQuizzesContent, takeQuizContent,
            studentQuizResultsContent, studentProfileContent;
    @FXML private studentManagementController studentContentController;
    @FXML private teacherManagementController teacherContentController;
    @FXML private quizManagementController quizContentController;
    @FXML private subjectCategoryManagementController subjectContentController;
    @FXML private reportsController reportsContentController;
    @FXML private resultsController resultsContentController;
    @FXML private accountManagementController accountContentController;
    @FXML private createQuizController createQuizContentController;
    @FXML private assignedSubjectsController assignedSubjectsContentController;
    @FXML private studentResultsController studentResultsContentController;
    @FXML private quizStatisticsController quizStatisticsContentController;
    @FXML private quizora.controllerTeacher.profileController teacherProfileContentController;
    @FXML private availableQuizzesController availableQuizzesContentController;
    @FXML private takeQuizController takeQuizContentController;
    @FXML private quizResultsController studentQuizResultsContentController;
    @FXML private quizora.controllerStudent.profileController studentProfileContentController;

    /** Sidebar button ID to its page, and what to load when that page opens. */
    private final Map<String, Node> pages = new HashMap<>();
    private final Map<String, Runnable> loaders = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        page("dashboardButton", dashboardContent, null);
        page("studentManagementButton", studentContent, () -> studentContentController.refresh());
        page("teacherManagementButton", teacherContent, () -> teacherContentController.refresh());
        page("quizManagementButton", quizContent, () -> quizContentController.refresh());
        page("subjectCategoryManagementButton", subjectContent, () -> subjectContentController.refresh());
        page("reportsButton", reportsContent, () -> reportsContentController.refresh());
        page("resultsButton", resultsContent, () -> resultsContentController.refresh());
        page("accountManagementButton", accountContent, () -> accountContentController.refresh());
        page("createQuizButton", createQuizContent, () -> createQuizContentController.open());
        page("assignedSubjectsButton", assignedSubjectsContent, () -> assignedSubjectsContentController.refresh());
        page("studentResultsButton", studentResultsContent, () -> studentResultsContentController.refresh());
        page("quizStatisticsButton", quizStatisticsContent, () -> quizStatisticsContentController.refresh());
        page("profileButton", teacherProfileContent, () -> teacherProfileContentController.refresh());
        page("availableQuizzesButton", availableQuizzesContent, () -> availableQuizzesContentController.refresh());
        page("takeQuizButton", takeQuizContent, () -> takeQuizContentController.open());
        page("quizResultsButton", studentQuizResultsContent, () -> studentQuizResultsContentController.refresh());
        page("profileButton", studentProfileContent, () -> studentProfileContentController.refresh());
        if (availableQuizzesContentController != null) {
            availableQuizzesContentController.setTakeQuizHandler(quizId -> {
                takeQuizContentController.selectQuiz(quizId);
                show(takeQuizButton);
            });
        }
    }

    private void page(String buttonId, Node content, Runnable loader) {
        if (content == null) return;
        pages.put(buttonId, content);
        if (loader != null) loaders.put(buttonId, loader);
    }

    @FXML
    private void navigate(ActionEvent event) {
        show((ToggleButton) event.getSource());
    }

    private void show(ToggleButton destination) {
        // Re-selecting keeps a page selected when its already-active button is clicked again.
        destination.setSelected(true);
        workspacePane.setAccessibleText(destination.getText() + " workspace");
        pages.forEach((buttonId, page) -> {
            boolean shown = buttonId.equals(destination.getId());
            page.setVisible(shown);
            page.setManaged(shown);
        });
        Runnable loader = loaders.get(destination.getId());
        if (loader != null) loader.run();
    }

    @FXML
    private void logout() throws IOException {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "You will be returned to the login screen.");
        alert.setTitle("Confirm Logout");
        alert.setHeaderText("Are you sure you want to log out?");
        alert.initOwner(workspacePane.getScene().getWindow());
        if (alert.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;
        UserSession.clear();
        Alert done = new Alert(Alert.AlertType.INFORMATION, "Logged out successfully.");
        done.setTitle("Logout");
        done.setHeaderText(null);
        done.initOwner(workspacePane.getScene().getWindow());
        done.showAndWait();
        new Quizora().start((Stage) workspacePane.getScene().getWindow());
    }
}
