/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora;

import java.io.IOException;
import java.net.URL;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX launcher for Quizora.
 *
 * <p>Satoshi fonts are registered by the {@code @font-face} rules in the
 * authentication and dashboard stylesheets, so no font code is needed here.</p>
 */
public class Quizora extends Application {

    public static final String LOGIN_VIEW_RESOURCE =
            "/Resources/fxml/authentication/login.fxml";
    public static final String AUTHENTICATION_STYLESHEET_RESOURCE =
            "/Resources/css/authentication.css";

    public static final double LOGIN_WIDTH = 1000.0;
    public static final double LOGIN_HEIGHT = 500.0;

    /** Launches JavaFX and opens the login screen. */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        quizora.auth.UserSession.clear();
        URL viewUrl = Quizora.class.getResource(LOGIN_VIEW_RESOURCE);
        URL stylesheetUrl = Quizora.class.getResource(AUTHENTICATION_STYLESHEET_RESOURCE);
        if (viewUrl == null || stylesheetUrl == null) {
            throw new IOException("Quizora login resources could not be found");
        }

        Parent root = FXMLLoader.load(viewUrl);
        Scene scene = new Scene(root, LOGIN_WIDTH, LOGIN_HEIGHT);
        scene.getStylesheets().add(stylesheetUrl.toExternalForm());

        stage.setTitle("Quizora - Login");
        stage.setMaximized(false);
        stage.setMinWidth(0);
        stage.setMinHeight(0);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        stage.sizeToScene();
        stage.centerOnScreen();

        Platform.runLater(() -> {
            Node usernameField = root.lookup("#usernameField");
            if (usernameField != null) {
                usernameField.requestFocus();
            }
        });
    }
}
