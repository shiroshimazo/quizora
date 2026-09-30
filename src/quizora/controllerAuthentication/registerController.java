/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.controllerAuthentication;


public class registerController {
    @javafx.fxml.FXML private javafx.scene.control.Hyperlink signInLink;

    @javafx.fxml.FXML
    private void backToLogin() {
        try {
            javafx.scene.Parent login = javafx.fxml.FXMLLoader.load(getClass().getResource(
                    "/Resources/fxml/authentication/login.fxml"));
            signInLink.getScene().setRoot(login);
            ((javafx.stage.Stage) login.getScene().getWindow()).setTitle("Quizora - Login");
            login.lookup("#usernameField").requestFocus();
        } catch (java.io.IOException failure) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR,
                    "Unable to open login. Please try again.");
            alert.initOwner(signInLink.getScene().getWindow());
            alert.setHeaderText("Unable to open login");
            alert.showAndWait();
        }
    }
}
