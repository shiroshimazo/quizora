package quizora.controllerAuthentication;

import java.io.IOException;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import quizora.auth.RegistrationService;

public class registerController {
    @FXML private TextField fullNameField, usernameField, emailField;
    @FXML private PasswordField passwordField, confirmPasswordField;
    @FXML private TextField visiblePasswordField, visibleConfirmPasswordField;
    @FXML private CheckBox showPasswordsCheckBox;
    @FXML private Button createAccountButton;
    @FXML private Hyperlink signInLink;
    private boolean busy;
    private boolean created;

    @FXML private void initialize() {
        bindVisibility(passwordField, visiblePasswordField);
        bindVisibility(confirmPasswordField, visibleConfirmPasswordField);
    }

    private void bindVisibility(PasswordField hidden, TextField visible) {
        visible.textProperty().bindBidirectional(hidden.textProperty());
        visible.visibleProperty().bind(showPasswordsCheckBox.selectedProperty());
        visible.managedProperty().bind(visible.visibleProperty());
        hidden.visibleProperty().bind(showPasswordsCheckBox.selectedProperty().not());
        hidden.managedProperty().bind(hidden.visibleProperty());
    }

    @FXML private void createAccount() {
        if (busy || created) return;
        String name = fullNameField.getText();
        String username = usernameField.getText();
        String email = emailField.getText();
        char[] password = passwordField.getText().toCharArray();
        char[] confirmation = confirmPasswordField.getText().toCharArray();
        setBusy(true);
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                new RegistrationService().register(name, username, email, password, confirmation);
                return null;
            }
        };
        task.setOnSucceeded(event -> accountCreated());
        task.setOnFailed(event -> {
            setBusy(false);
            Throwable failure = task.getException();
            showError(failure instanceof IllegalArgumentException ? failure.getMessage()
                    : "Unable to create your account. Check the database connection and try again.");
        });
        Thread worker = new Thread(task, "quizora-registration");
        worker.setDaemon(true);
        worker.start();
    }

    private void accountCreated() {
        created = true;
        setBusy(false);
        passwordField.clear();
        confirmPasswordField.clear();
        showPasswordsCheckBox.setSelected(false);
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "Successfully Created Account, you can now Login", ButtonType.OK);
        alert.initOwner(signInLink.getScene().getWindow());
        alert.setTitle("Account created");
        alert.setHeaderText(null);
        alert.showAndWait();
        backToLogin();
    }

    private void setBusy(boolean value) {
        busy = value;
        for (Control control : new Control[]{fullNameField, usernameField, emailField, passwordField,
                confirmPasswordField, visiblePasswordField, visibleConfirmPasswordField, showPasswordsCheckBox})
            control.setDisable(value || created);
        createAccountButton.setDisable(value || created);
        signInLink.setDisable(value);
        createAccountButton.setText(value ? "Creating account..." : created ? "Account created" : "Create account");
    }

    @FXML private void backToLogin() {
        if (busy) return;
        try {
            Parent login = FXMLLoader.load(getClass().getResource("/Resources/fxml/authentication/login.fxml"));
            if (created) {
                ((TextField) login.lookup("#usernameField")).setText(usernameField.getText().strip());
                ((Label) login.lookup("#loginStatusLabel")).setText("Account created. Sign in to continue.");
            }
            passwordField.clear();
            confirmPasswordField.clear();
            signInLink.getScene().setRoot(login);
            ((Stage) login.getScene().getWindow()).setTitle("Quizora - Login");
            login.lookup(created ? "#passwordField" : "#usernameField").requestFocus();
        } catch (IOException failure) {
            showError(created ? "Your account was created. Use Sign in to return to login."
                    : "Unable to open login. Please try again.");
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(signInLink.getScene().getWindow());
        alert.setHeaderText(created ? "Account created" : "Registration");
        alert.showAndWait();
    }
}
