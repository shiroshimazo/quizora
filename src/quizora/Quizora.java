package quizora;

import java.io.IOException;
import java.net.URL;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * JavaFX launcher for Quizora.
 *
 * <p>All bundled Satoshi static faces are loaded before the FXML scene is
 * created, making every weight available to the stylesheet and future
 * JavaFX controls.</p>
 */
public class Quizora extends Application {

    public static final String SATOSHI_FONT_RESOURCE =
            "/Resources/fonts/Satoshi-Variable.ttf";
    public static final String SATOSHI_REGULAR_FONT_RESOURCE =
            "/Resources/fonts/Satoshi-Regular.ttf";
    public static final String LOGIN_VIEW_RESOURCE =
            "/Resources/fxml/authentication/login.fxml";
    public static final String AUTHENTICATION_STYLESHEET_RESOURCE =
            "/Resources/css/authentication.css";

    public static final double LOGIN_WIDTH = 1000.0;
    public static final double LOGIN_HEIGHT = 500.0;
    public static final double DEFAULT_FONT_SIZE = 14.0;

    /** The named weights provided by the bundled Satoshi family. */
    public enum SatoshiWeight {
        LIGHT(300, FontWeight.LIGHT, "Satoshi Variable Light", "/Resources/fonts/Satoshi-Light.ttf"),
        REGULAR(400, FontWeight.NORMAL, "Satoshi Variable Regular", "/Resources/fonts/Satoshi-Regular.ttf"),
        MEDIUM(500, FontWeight.MEDIUM, "Satoshi Variable Medium", "/Resources/fonts/Satoshi-Medium.ttf"),
        SEMI_BOLD(600, FontWeight.SEMI_BOLD, "Satoshi Variable SemiBold", "/Resources/fonts/Satoshi-SemiBold.ttf"),
        BOLD(700, FontWeight.BOLD, "Satoshi Variable Bold", "/Resources/fonts/Satoshi-Bold.ttf"),
        EXTRA_BOLD(800, FontWeight.EXTRA_BOLD, "Satoshi Variable ExtraBold", "/Resources/fonts/Satoshi-ExtraBold.ttf"),
        BLACK(900, FontWeight.BLACK, "Satoshi Variable Black", "/Resources/fonts/Satoshi-Black.ttf");

        private final int cssWeight;
        private final FontWeight javafxWeight;
        private final String fontName;
        private final String resource;

        SatoshiWeight(int cssWeight, FontWeight javafxWeight, String fontName, String resource) {
            this.cssWeight = cssWeight;
            this.javafxWeight = javafxWeight;
            this.fontName = fontName;
            this.resource = resource;
        }

        public int value() {
            return cssWeight;
        }
    }

    /* Register every static face before CSS is parsed. JavaFX does not select
       the wght axis reliably when only a variable TTF is registered. */
    private static final Font SATOSHI_BASE_FONT = loadSatoshiFonts();

    /** Actual family name reported by the bundled font. */
    public static final String SATOSHI_FAMILY = SATOSHI_BASE_FONT.getFamily();

    public static final Font SATOSHI_LIGHT = satoshi(SatoshiWeight.LIGHT, DEFAULT_FONT_SIZE);
    public static final Font SATOSHI_REGULAR = satoshi(SatoshiWeight.REGULAR, DEFAULT_FONT_SIZE);
    public static final Font SATOSHI_MEDIUM = satoshi(SatoshiWeight.MEDIUM, DEFAULT_FONT_SIZE);
    public static final Font SATOSHI_SEMI_BOLD = satoshi(SatoshiWeight.SEMI_BOLD, DEFAULT_FONT_SIZE);
    public static final Font SATOSHI_BOLD = satoshi(SatoshiWeight.BOLD, DEFAULT_FONT_SIZE);
    public static final Font SATOSHI_EXTRA_BOLD = satoshi(SatoshiWeight.EXTRA_BOLD, DEFAULT_FONT_SIZE);
    public static final Font SATOSHI_BLACK = satoshi(SatoshiWeight.BLACK, DEFAULT_FONT_SIZE);

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
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        stage.sizeToScene();

        // Match the reference state and make the first field ready for typing.
        Platform.runLater(() -> {
            Node usernameField = root.lookup("#usernameField");
            if (usernameField != null) {
                usernameField.requestFocus();
            }
        });
    }

    /** Returns regular Satoshi at the requested point size. */
    public static Font satoshi(double size) {
        return satoshi(SatoshiWeight.REGULAR, size);
    }

    /** Returns Satoshi at a named weight and requested point size. */
    public static Font satoshi(SatoshiWeight weight, double size) {
        if (weight == null) {
            throw new IllegalArgumentException("Satoshi weight cannot be null");
        }
        if (!Double.isFinite(size) || size <= 0.0) {
            throw new IllegalArgumentException(
                    "Font size must be finite and greater than zero: " + size);
        }
        // Use the exact face name. JavaFX may collapse multiple static faces
        // that share one family name and otherwise resolve every request to
        // the first/bold face it registered.
        return Font.font(weight.fontName, size);
    }

    /**
     * Returns the closest named Satoshi weight for a CSS/OpenType weight from
     * 300 through 900.
     */
    public static Font satoshi(int cssWeight, double size) {
        if (cssWeight < SatoshiWeight.LIGHT.value()
                || cssWeight > SatoshiWeight.BLACK.value()) {
            throw new IllegalArgumentException(
                    "Satoshi weight must be between 300 and 900: " + cssWeight);
        }

        SatoshiWeight nearest = SatoshiWeight.LIGHT;
        int distance = Integer.MAX_VALUE;
        for (SatoshiWeight candidate : SatoshiWeight.values()) {
            int candidateDistance = Math.abs(candidate.value() - cssWeight);
            if (candidateDistance < distance) {
                nearest = candidate;
                distance = candidateDistance;
            }
        }
        return satoshi(nearest, size);
    }

    /** Returns the bundled font URL for direct JavaFX font loading. */
    public static URL satoshiFontUrl() {
        URL resource = Quizora.class.getResource(SATOSHI_REGULAR_FONT_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException(
                    "Satoshi font resource not found: " + SATOSHI_REGULAR_FONT_RESOURCE);
        }
        return resource;
    }

    private static Font loadSatoshiFonts() {
        Font regular = null;
        for (SatoshiWeight weight : SatoshiWeight.values()) {
            URL resource = Quizora.class.getResource(weight.resource);
            if (resource == null) {
                throw new IllegalStateException("Satoshi font resource not found: " + weight.resource);
            }
            Font loaded = Font.loadFont(resource.toExternalForm(), DEFAULT_FONT_SIZE);
            if (loaded == null) {
                throw new IllegalStateException("Unable to load Satoshi font from " + weight.resource);
            }
            if (weight == SatoshiWeight.REGULAR) {
                regular = loaded;
            }
        }
        return regular;
    }
}
