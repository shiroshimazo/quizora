package quizora.controllerAuthentication;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class RegistrationUiTest extends Application {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    @Override public void start(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Resources/fxml/authentication/register.fxml"));
            Parent root = loader.load();
            stage.setScene(new Scene(root));
            root.applyCss(); root.layout();
            PasswordField hidden = (PasswordField) root.lookup("#passwordField");
            PasswordField confirm = (PasswordField) root.lookup("#confirmPasswordField");
            TextField visible = (TextField) root.lookup("#visiblePasswordField");
            TextField visibleConfirm = (TextField) root.lookup("#visibleConfirmPasswordField");
            CheckBox toggle = (CheckBox) root.lookup("#showPasswordsCheckBox");
            hidden.setText("secret123"); confirm.setText("confirm123");
            check(!visible.isVisible() && hidden.isVisible(), "Passwords initially hidden");
            toggle.setSelected(true);
            root.applyCss(); root.layout();
            check(visible.getPadding().getLeft() == visibleConfirm.getPadding().getLeft(), "Matching visible password padding");
            check(visible.getPadding().getLeft() == hidden.getPadding().getLeft(), "No extra padding when password shown");
            check(visible.isVisible() && !hidden.isVisible() && visibleConfirm.isVisible(), "Show both passwords");
            check(visible.getText().equals("secret123"), "Keep password when shown");
            visibleConfirm.setText("edited123");
            toggle.setSelected(false);
            check(confirm.getText().equals("edited123") && !visibleConfirm.isManaged(), "Sync edits when hidden");
            Button submit = (Button) root.lookup("#createAccountButton");
            check(submit.getOnAction() != null && submit.isDefaultButton(), "Submit and Enter wired");
            stage.show();
            Platform.runLater(() -> {
                try {
                    check(stage.getScene().getRoot() == root, "Stay on registration until dialog dismissed");
                    DialogPane dialog = javafx.stage.Window.getWindows().stream()
                            .filter(window -> window != stage && window.isShowing())
                            .map(window -> window.getScene().getRoot())
                            .filter(node -> node instanceof DialogPane)
                            .map(node -> (DialogPane) node).findFirst().orElseThrow();
                    check(dialog.getContentText().equals("Successfully Created Account, you can now Login"), "Success message");
                    check(dialog.getButtonTypes().equals(javafx.collections.FXCollections.observableArrayList(ButtonType.OK)), "Only OK button");
                    ((Button)dialog.lookupButton(ButtonType.OK)).fire();
                } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
            });
            var success = registerController.class.getDeclaredMethod("accountCreated");
            success.setAccessible(true);
            success.invoke(loader.getController());
            check(stage.getScene().getRoot().lookup("#loginButton") != null, "Sign in navigation");
            check(hidden.getText().isEmpty() && confirm.getText().isEmpty(), "Clear passwords on navigation");
            System.out.println("Registration UI checks passed (padding, visibility, success dialog, OK then login).");
            Platform.exit();
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    public static void main(String[] args) { launch(args); }
}
