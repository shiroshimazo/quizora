/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.ui;

import java.io.IOException;
import java.util.Properties;
import javafx.scene.control.Labeled;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/** Locally bundled Hugeicons Stroke Rounded graphics for JavaFX controls. */
public final class HugeIcon extends Text {
    private static final String BASE = "/Resources/icons/hugeicons/";
    private static final Properties GLYPHS = loadGlyphs();
    private static final Font FONT = loadFont();
    private String name;

    public HugeIcon() {
        setFont(FONT);
        setStyle("-fx-font-family: '" + FONT.getFamily() + "'; -fx-font-size: 18px;");
        setMouseTransparent(true);
        setFocusTraversable(false);
        setAccessibleText("");
        getStyleClass().add("huge-icon");
        parentProperty().addListener((observable, previous, parent) -> {
            fillProperty().unbind();
            if (parent instanceof Labeled control) {
                fillProperty().bind(control.textFillProperty());
            }
        });
    }

    public HugeIcon(String name) {
        this();
        setName(name);
    }

    public String getName() { return name; }

    public void setName(String name) {
        String code = GLYPHS.getProperty(name);
        if (code == null) throw new IllegalArgumentException("Unknown Hugeicons icon: " + name);
        this.name = name;
        setText(new String(Character.toChars(Integer.parseInt(code, 16))));
    }

    public static void attach(Labeled control, String name) {
        HugeIcon icon = new HugeIcon(name);
        icon.fillProperty().bind(control.textFillProperty());
        control.setGraphic(icon);
        control.setGraphicTextGap(8);
    }

    private static Properties loadGlyphs() {
        Properties glyphs = new Properties();
        try (var stream = HugeIcon.class.getResourceAsStream(BASE + "glyphs.properties")) {
            if (stream == null) throw new IllegalStateException("Hugeicons glyph map is missing");
            glyphs.load(stream);
            return glyphs;
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Font loadFont() {
        var resource = HugeIcon.class.getResource(BASE + "hgi-stroke-rounded.ttf");
        Font font = resource == null ? null : Font.loadFont(resource.toExternalForm(), 18);
        if (font == null) throw new IllegalStateException("Hugeicons font could not be loaded");
        return font;
    }
}
