package quizora.controllerAdmin;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import quizora.model.StudentChanges;
import quizora.model.StudentRecord;

public class StudentEditController {
    @FXML private TextField nameField, usernameField, emailField;
    @FXML private ComboBox<String> editStatusFilter;
    @FXML private Label editMessage;

    public void populate(StudentRecord student) {
        nameField.setText(student.name());
        usernameField.setText(student.username());
        emailField.setText(student.email());
        editStatusFilter.getItems().setAll("Active", "Inactive");
        editStatusFilter.setValue(student.status());
    }

    public StudentChanges changes() {
        if (editStatusFilter.getValue() == null) throw new IllegalArgumentException("Select a status.");
        return new StudentChanges(nameField.getText(), usernameField.getText(), emailField.getText(),
                "Active".equals(editStatusFilter.getValue()));
    }

    public void message(String text) { editMessage.setText(text); }
}
