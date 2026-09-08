package quizora.controllerAdmin;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import quizora.model.AccountChanges;
import quizora.model.AccountRecord;

public class StudentEditController {
    @FXML private TextField nameField, usernameField, emailField;
    @FXML private ComboBox<String> editStatusFilter;
    @FXML private Label editMessage;
    @FXML private Label formHeading;
    @FXML private VBox passwordFields;
    @FXML private PasswordField passwordField, confirmPasswordField;

    public void prepareCreate() {
        formHeading.setText("Add a new student");
        passwordFields.setVisible(true);
        passwordFields.setManaged(true);
        editStatusFilter.getItems().setAll("Active", "Inactive");
        editStatusFilter.setValue("Active");
    }

    public String password() {
        String password = passwordField.getText();
        quizora.DAO.StudentManagementDAO.validatePassword(password);
        if (!password.equals(confirmPasswordField.getText()))
            throw new IllegalArgumentException("Passwords do not match.");
        return password;
    }

    public void clearPassword() {
        passwordField.clear();
        confirmPasswordField.clear();
    }

    public void populate(AccountRecord student) {
        nameField.setText(student.name());
        usernameField.setText(student.username());
        emailField.setText(student.email());
        editStatusFilter.getItems().setAll("Active", "Inactive");
        editStatusFilter.setValue(student.status());
    }

    public AccountChanges changes() {
        if (editStatusFilter.getValue() == null) throw new IllegalArgumentException("Select a status.");
        return new AccountChanges(nameField.getText(), usernameField.getText(), emailField.getText(),
                "Active".equals(editStatusFilter.getValue()));
    }

    public void message(String text) { editMessage.setText(text); }
}
