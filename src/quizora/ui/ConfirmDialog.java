/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.ui;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/** A yes/no question shown before saving. Cancel keeps the form open so nothing is lost. */
public final class ConfirmDialog {

    private ConfirmDialog() { }

    /** Returns true only when the user clicks OK. */
    public static boolean ask(Node owner, String question, String details) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, details, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Confirm");
        alert.setHeaderText(question);
        alert.initOwner(owner.getScene().getWindow());
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
}
