/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

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
    @FXML private ToggleButton showPasswordButton;
    @FXML private Button loginButton;
    @FXML private Label loginStatusLabel;
    private boolean busy;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        visiblePasswordField.visibleProperty().bind(showPasswordButton.selectedProperty());
        visiblePasswordField.managedProperty().bind(visiblePasswordField.visibleProperty());
        passwordField.visibleProperty().bind(showPasswordButton.selectedProperty().not());
        passwordField.managedProperty().bind(passwordField.visibleProperty());
        Tooltip visibilityHint = new Tooltip("Show password");
        showPasswordButton.setTooltip(visibilityHint);
        quizora.ui.HugeIcon.attach(showPasswordButton, "view-off");
        showPasswordButton.selectedProperty().addListener((observable, previous, shown) -> {
            TextField source = shown ? passwordField : visiblePasswordField;
            TextField target = passwordInput();
            int anchor = source.getAnchor();
            int caret = source.getCaretPosition();
            quizora.ui.HugeIcon.attach(showPasswordButton, shown ? "view" : "view-off");
            visibilityHint.setText(shown ? "Hide password" : "Show password");
            showPasswordButton.setAccessibleText(visibilityHint.getText());
            target.requestFocus();
            target.selectRange(anchor, caret);
        });
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

    @FXML
    private void forgotPassword() {
        if (busy) return;
        try {
            javafx.scene.Parent recovery = javafx.fxml.FXMLLoader.load(getClass().getResource(
                    "/Resources/fxml/authentication/forgotPassword.fxml"));
            passwordField.clear();
            loginButton.getScene().setRoot(recovery);
            ((Stage) recovery.getScene().getWindow()).setTitle("Quizora - Forgot password");
        } catch (java.io.IOException failure) {
            loginStatusLabel.setText("Unable to open password recovery. Please try again.");
        }
    }

    @FXML
    private void createAccount() {
        if (busy) return;
        try {
            javafx.scene.Parent registration = javafx.fxml.FXMLLoader.load(getClass().getResource(
                    "/Resources/fxml/authentication/register.fxml"));
            passwordField.clear();
            loginButton.getScene().setRoot(registration);
            ((Stage) registration.getScene().getWindow()).setTitle("Quizora - Create Account");
            registration.lookup("#fullNameField").requestFocus();
        } catch (java.io.IOException failure) {
            loginStatusLabel.setText("Unable to open registration. Please try again.");
        }
    }

    private TextField passwordInput() {
        return showPasswordButton.isSelected() ? visiblePasswordField : passwordField;
    }

    private void setBusy(boolean value) {
        busy = value;
        loginButton.setDisable(value);
        usernameField.setDisable(value);
        passwordField.setDisable(value);
        visiblePasswordField.setDisable(value);
        showPasswordButton.setDisable(value);
        loginButton.setText(value ? "Signing in..." : "Login");
    }
}
