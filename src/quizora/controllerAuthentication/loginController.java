package quizora.controllerAuthentication;

import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import quizora.auth.*;
import quizora.dashboard.PanelRouter;

public class loginController implements Initializable {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private CheckBox showPasswordCheckBox;
    @FXML private Button loginButton;
    @FXML private Label loginStatusLabel;
    private boolean busy;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        visiblePasswordField.visibleProperty().bind(showPasswordCheckBox.selectedProperty());
        visiblePasswordField.managedProperty().bind(visiblePasswordField.visibleProperty());
        passwordField.visibleProperty().bind(showPasswordCheckBox.selectedProperty().not());
        passwordField.managedProperty().bind(passwordField.visibleProperty());
    }

    @FXML
    private void login() {
        if (busy) return;
        if (usernameField.getText().isBlank() || passwordField.getText().isEmpty()) {
            loginStatusLabel.setText("Enter your username or email and password.");
            if (usernameField.getText().isBlank()) usernameField.requestFocus();
            else passwordInput().requestFocus();
            return;
        }
        String identifier = usernameField.getText().trim();
        char[] password = passwordField.getText().toCharArray();
        UserSession.clear();
        setBusy(true);
        loginStatusLabel.setText("Signing in...");
        Task<Optional<AuthenticatedUser>> task = new Task<>() {
            @Override protected Optional<AuthenticatedUser> call() throws Exception {
                try {
                    return new AuthenticationService().authenticate(identifier, password);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            passwordField.clear();
            if (task.getValue().isEmpty()) {
                loginStatusLabel.setText("Invalid credentials or inactive account.");
                passwordInput().requestFocus();
                return;
            }
            try {
                UserSession.signIn(task.getValue().get());
                PanelRouter.open((Stage) loginButton.getScene().getWindow());
            } catch (Exception error) {
                UserSession.clear();
                loginStatusLabel.setText("Unable to open your panel. Please try again.");
            }
        });
        task.setOnFailed(event -> {
            setBusy(false);
            passwordField.clear();
            UserSession.clear();
            loginStatusLabel.setText("Unable to sign in. Check the database connection.");
            passwordInput().requestFocus();
        });
        Thread worker = new Thread(task, "quizora-login");
        worker.setDaemon(true);
        worker.start();
    }

    private TextField passwordInput() {
        return showPasswordCheckBox.isSelected() ? visiblePasswordField : passwordField;
    }

    private void setBusy(boolean value) {
        busy = value;
        loginButton.setDisable(value);
        usernameField.setDisable(value);
        passwordField.setDisable(value);
        visiblePasswordField.setDisable(value);
        showPasswordCheckBox.setDisable(value);
        loginButton.setText(value ? "Signing in..." : "Login");
    }
}
