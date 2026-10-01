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
import java.io.UncheckedIOException;
import java.util.Properties;
import javafx.scene.control.Labeled;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public final class HugeIcon extends Text {
    private static final String BASE = "/Resources/icons/hugeicons/";
    private static final Properties GLYPHS = new Properties();
    private static final Font FONT =
            Font.loadFont(HugeIcon.class.getResource(BASE + "hgi-stroke-rounded.ttf").toExternalForm(), 18);

    static {
        try (var stream = HugeIcon.class.getResourceAsStream(BASE + "glyphs.properties")) {
            GLYPHS.load(stream);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private String name;

    public HugeIcon() {
        setFont(FONT);
        setStyle("-fx-font-family: '" + FONT.getFamily() + "'; -fx-font-size: 18px;");
        setMouseTransparent(true);
        setAccessibleText("");
        parentProperty().addListener((observable, previous, parent) -> {
            fillProperty().unbind();
            if (parent instanceof Labeled control) fillProperty().bind(control.textFillProperty());
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
        setText(Character.toString(Integer.parseInt(code, 16)));
    }

    public static void attach(Labeled control, String name) {
        control.setGraphic(new HugeIcon(name));
        control.setGraphicTextGap(8);
    }
}
