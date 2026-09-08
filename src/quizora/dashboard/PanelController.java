package quizora.dashboard;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import quizora.Quizora;

/** Shared navigation behavior for the three presentation-only role shells. */
public class PanelController implements Initializable {
    @FXML private ToggleGroup navigationGroup;
    @FXML private StackPane workspacePane;

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
        // Feature screens are intentionally blank in this implementation phase.
    }

    @FXML
    private void logout(ActionEvent event) throws IOException {
        quizora.auth.UserSession.clear();
        new Quizora().start((Stage) workspacePane.getScene().getWindow());
    }
}
