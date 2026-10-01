/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.controllerAuthentication;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import quizora.auth.PasswordRecoveryService;
import quizora.auth.PasswordRecoveryService.RecoveryException;
import quizora.auth.PasswordRecoveryService.Ticket;

/** Three-step recovery with background delivery and an absolute-deadline countdown. */
public class forgotPasswordController {
    @FXML private VBox emailStep, codeStep, passwordStep;
    @FXML private Label stepOne, stepTwo, stepThree, recoveryStatus, destinationLabel;
    @FXML private Label sentAtLabel, expiresAtLabel, countdownLabel;
    @FXML private TextField emailField, codeField;
    @FXML private PasswordField newPasswordField, confirmPasswordField;
    @FXML private TextField visibleNewPasswordField, visibleConfirmPasswordField;
    @FXML private ToggleButton showNewPasswordButton, showConfirmPasswordButton;
    @FXML private Button continueButton, resendButton;
    @FXML private Hyperlink previousButton, loginLink;
    private final PasswordRecoveryService service;
    private final Clock clock;
    private final Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateCountdown()));
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH).withZone(ZoneId.systemDefault());
    private Ticket ticket;
    private String recoveryEmail;
    private Instant nextResend = Instant.EPOCH;
    private int step = 1;
    private boolean busy, disposed, unusable;

    public forgotPasswordController() { this(new PasswordRecoveryService(), Clock.systemUTC()); }
    public forgotPasswordController(PasswordRecoveryService service, Clock clock) {
        this.service = service; this.clock = clock;
    }

    @FXML private void initialize() {
        configureVisibility(newPasswordField, visibleNewPasswordField, showNewPasswordButton, "new password");
        configureVisibility(confirmPasswordField, visibleConfirmPasswordField, showConfirmPasswordButton, "confirm password");
        codeField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("[0-9]{0,6}") ? change : null));
        countdown.setCycleCount(Timeline.INDEFINITE);
        emailStep.sceneProperty().addListener((observable, previous, scene) -> {
            if (previous != null && scene == null) { disposed = true; countdown.stop(); }
        });
        showStep(1);
    }

    private void configureVisibility(PasswordField masked, TextField visible, ToggleButton toggle, String label) {
        visible.textProperty().bindBidirectional(masked.textProperty());
        visible.visibleProperty().bind(toggle.selectedProperty());
        visible.managedProperty().bind(visible.visibleProperty());
        masked.visibleProperty().bind(toggle.selectedProperty().not());
        masked.managedProperty().bind(masked.visibleProperty());
        Tooltip tooltip = new Tooltip("Show " + label);
        toggle.setTooltip(tooltip);
        toggle.setAccessibleText(tooltip.getText());
        quizora.ui.HugeIcon.attach(toggle, "view-off");
        toggle.selectedProperty().addListener((observable, previous, shown) -> {
            TextField source = shown ? masked : visible;
            TextField target = shown ? visible : masked;
            int anchor = source.getAnchor();
            int caret = source.getCaretPosition();
            quizora.ui.HugeIcon.attach(toggle, shown ? "view" : "view-off");
            tooltip.setText((shown ? "Hide " : "Show ") + label);
            toggle.setAccessibleText(tooltip.getText());
            target.requestFocus();
            target.selectRange(anchor, caret);
        });
    }

    @FXML private void continueRecovery() {
        if (busy) return;
        recoveryStatus.setText("");
        if (step == 1) {
            String email = emailField.getText().strip();
            if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
                recoveryStatus.setText("Enter a valid email address.");
                emailField.requestFocus();
                return;
            }
            recoveryEmail = email;
            sendCode();
        } else if (step == 2) {
            if (ticket == null || unusable || !clock.instant().isBefore(ticket.expiresAt())) {
                updateCountdown();
                if (ticket != null) recoveryStatus.setText(PasswordRecoveryService.EXPIRED);
                return;
            }
            Ticket current = ticket;
            String code = codeField.getText();
            run(() -> { service.verify(current, code); return null; }, ignored -> showStep(3), "Verifying code...");
        } else {
            char[] password = newPasswordField.getText().toCharArray();
            char[] confirmation = confirmPasswordField.getText().toCharArray();
            Ticket current = ticket;
            run(() -> { service.resetPassword(current, password, confirmation); return null; }, ignored -> {
                newPasswordField.clear(); confirmPasswordField.clear(); ticket = null;
                dialog(Alert.AlertType.INFORMATION, "Password reset", "Your password has been updated. You can now sign in.").showAndWait();
                backToLogin();
            }, "Updating password...");
        }
    }

    @FXML private void resendCode() {
        if (!busy && !clock.instant().isBefore(nextResend)) sendCode();
    }

    private void sendCode() {
        ticket = null;
        unusable = false;
        nextResend = clock.instant().plusSeconds(30);
        sentAtLabel.setText("Sending verification code...");
        expiresAtLabel.setText("");
        countdownLabel.setText("");
        run(() -> service.sendCode(recoveryEmail), sent -> {
            ticket = sent;
            destinationLabel.setText("Enter the 6-digit code sent to " + sent.email() + ".");
            sentAtLabel.setText("Code sent at " + timeFormat.format(sent.sentAt()));
            expiresAtLabel.setText("Expires at " + timeFormat.format(sent.expiresAt()));
            showStep(2);
        }, "Sending verification code...");
    }

    @FXML private void previousStep() {
        if (busy) return;
        if (step > 1) { ticket = null; showStep(1); }
        else backToLogin();
    }

    @FXML private void backToLogin() {
        if (busy) return;
        try {
            Parent login = FXMLLoader.load(getClass().getResource("/Resources/fxml/authentication/login.fxml"));
            countdown.stop();
            newPasswordField.clear(); confirmPasswordField.clear();
            emailField.getScene().setRoot(login);
            ((Stage) login.getScene().getWindow()).setTitle("Quizora - Login");
        } catch (IOException failure) {
            recoveryStatus.setText("Unable to open login. Please try again.");
        }
    }

    private void showStep(int number) {
        step = number;
        VBox[] panels = {emailStep, codeStep, passwordStep};
        Label[] markers = {stepOne, stepTwo, stepThree};
        for (int index = 0; index < panels.length; index++) {
            panels[index].setVisible(index == number - 1);
            panels[index].setManaged(index == number - 1);
            markers[index].getStyleClass().removeAll("recovery-current", "recovery-complete");
            if (index == number - 1) markers[index].getStyleClass().add("recovery-current");
            else if (index < number - 1) markers[index].getStyleClass().add("recovery-complete");
        }
        codeField.clear(); newPasswordField.clear(); confirmPasswordField.clear();
        showNewPasswordButton.setSelected(false); showConfirmPasswordButton.setSelected(false);
        recoveryStatus.setText("");
        continueButton.setText(number == 1 ? "Send code" : number == 2 ? "Verify code" : "Reset password");
        quizora.ui.HugeIcon.attach(continueButton, number == 3 ? "lock-password" : "arrow-left-01");
        if (number < 3) continueButton.getGraphic().setRotate(180);
        if (number == 2) countdown.playFromStart(); else countdown.stop();
        updateCountdown();
        (number == 1 ? emailField : number == 2 ? codeField : newPasswordField).requestFocus();
    }

    private void updateCountdown() {
        if (disposed) { countdown.stop(); return; }
        Instant now = clock.instant();
        boolean expired = ticket != null && !now.isBefore(ticket.expiresAt());
        if (step == 2 && ticket != null) {
            long millis = Math.max(0, java.time.Duration.between(now, ticket.expiresAt()).toMillis());
            long seconds = (millis + 999) / 1000;
            countdownLabel.setText(String.format(Locale.ROOT, "Time remaining: %d:%02d", seconds / 60, seconds % 60));
            if (expired && !busy) recoveryStatus.setText(PasswordRecoveryService.EXPIRED);
        }
        continueButton.setDisable(busy || step == 2 && (ticket == null || expired || unusable));
        codeField.setDisable(busy || ticket == null || expired || unusable);
        resendButton.setDisable(busy || now.isBefore(nextResend));
        long wait = Math.max(0, (java.time.Duration.between(now, nextResend).toMillis() + 999) / 1000);
        resendButton.setText(wait > 0 ? "Resend Code (" + wait + "s)" : "Resend Code");
    }

    private void setBusy(boolean value) {
        busy = value;
        emailField.setDisable(value); passwordStep.setDisable(value);
        previousButton.setDisable(value); loginLink.setDisable(value);
        updateCountdown();
    }

    private <T> void run(Callable<T> action, Consumer<T> success, String message) {
        setBusy(true); recoveryStatus.setText(message);
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return action.call(); } };
        task.setOnSucceeded(event -> { if (!disposed) { setBusy(false); success.accept(task.getValue()); } });
        task.setOnFailed(event -> { if (!disposed) { setBusy(false); showFailure(task.getException()); } });
        Thread worker = new Thread(task, "quizora-password-recovery");
        worker.setDaemon(true); worker.start();
    }

    private void showFailure(Throwable failure) {
        String message = failure instanceof RecoveryException error ? error.getMessage() : "Unable to complete your request. Please try again.";
        if (failure instanceof RecoveryException error) {
            switch (error.reason()) {
                case UNKNOWN_EMAIL -> {
                    recoveryStatus.setText("");
                    ButtonType signUp = new ButtonType("Sign up", ButtonBar.ButtonData.OK_DONE);
                    Alert alert = dialog(Alert.AlertType.CONFIRMATION, "Account not found", message);
                    alert.getButtonTypes().setAll(signUp, ButtonType.CANCEL);
                    alert.showAndWait().filter(signUp::equals).ifPresent(choice -> openRegistration());
                    return;
                }
                case INCORRECT -> {
                    dialog(Alert.AlertType.ERROR, "Incorrect verification code", message).showAndWait();
                    codeField.selectAll(); codeField.requestFocus();
                }
                case EXPIRED, SUPERSEDED, ATTEMPTS -> { unusable = true; showStep(2); }
                case DELIVERY -> { sentAtLabel.setText("No code was sent. Please try again."); expiresAtLabel.setText(""); }
                default -> { }
            }
        }
        recoveryStatus.setText(message);
        updateCountdown();
    }

    private void openRegistration() {
        try {
            Parent registration = FXMLLoader.load(getClass().getResource("/Resources/fxml/authentication/register.fxml"));
            ((TextField) registration.lookup("#emailField")).setText(emailField.getText().strip());
            countdown.stop();
            ticket = null;
            codeField.clear(); newPasswordField.clear(); confirmPasswordField.clear();
            emailField.getScene().setRoot(registration);
            ((Stage) registration.getScene().getWindow()).setTitle("Quizora - Create Account");
            registration.lookup("#fullNameField").requestFocus();
        } catch (IOException failure) {
            recoveryStatus.setText("Unable to open registration. Please try again.");
        }
    }

    private Alert dialog(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.initOwner(emailField.getScene().getWindow());
        alert.setTitle(title); alert.setHeaderText(title); alert.setContentText(message);
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/Resources/css/authentication.css").toExternalForm());
        return alert;
    }
}
