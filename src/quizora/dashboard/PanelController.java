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
        if (dashboardContent != null) {
            boolean dashboard = "dashboardButton".equals(destination.getId());
            dashboardContent.setVisible(dashboard);
            dashboardContent.setManaged(dashboard);
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
