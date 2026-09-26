# JavaFX UI Development Rules

## Framework and screen structure

Build the desktop UI with JavaFX (OpenJFX). Use one primary Stage owned by the
application window class, view roots (Parent nodes) for screens, and owned
Dialog/Alert or secondary Stage windows for modal editing and confirmation.
Switch authentication screens and role workspaces by replacing the Scene root
or the content of a StackPane host. Dashboard shells use a BorderPane with
sidebar navigation on the left and a central workspace.

Use GridPane for forms, VBox/HBox for vertical and horizontal groups, and
FlowPane/TilePane where cards must wrap. Avoid absolute positioning (Pane with
manual setLayoutX/Y) and fixed control sizes. Layout panes do not automatically
provide breakpoint reflow; explicitly adapt card/chart column counts by
listening to the workspace width property.

FXML with Scene Builder is optional. Keep each .fxml file beside its matching
controller class, give nodes meaningful fx:id values, and edit FXML layouts
through Scene Builder or by hand-editing the FXML, never by generating Java
layout code from it. Attach presentation behavior in the controller's
initialize() method or through FXML event handlers (onAction="#handleLogin").

## Components and naming

| Component | Example field / fx:id |
| --- | --- |
| Label | titleLabel |
| Button | loginButton |
| TextField | usernameField |
| PasswordField | passwordField |
| VBox / BorderPane | sidebarPane |
| TableView | studentTable |
| ComboBox | subjectComboBox |
| ScrollPane | quizScrollPane |

Back record tables with an ObservableList of models and TableColumn cell value
factories. Keep view classes and FXML focused on layout and rendering;
presentation controllers handle navigation, event handlers, validation
feedback, and background task lifecycle. Services own business validation,
authorization, quiz scoring, and transaction coordination; DAOs own SQL.

## Threading and events

The entry point launches JavaFX; start() runs on the JavaFX Application Thread:

```java
public class quizoraFxApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        // Initialize the shared theme, wire dependencies, and show the app window here.
    }
}
```

Keep `quizoraApp.main` as the configured main class and have it call
`Application.launch(quizoraFxApp.class, args)`. A main class that does not
extend Application avoids "JavaFX runtime components are missing" when the app
runs from the classpath.

Use setOnAction handlers or FXML onAction methods for commands. Run
authentication, database operations, file reading, and expensive calculations
in a javafx.concurrent.Task (or Service for repeatable work) on a background
executor. Update controls in setOnSucceeded/setOnFailed handlers, through
updateMessage/updateProgress bindings, or with Platform.runLater(); all of these
run on the JavaFX Application Thread. Never block the FX thread waiting for a
task to complete. Handle task failures, restore controls, and show meaningful
error messages. Cancel obsolete tasks where possible and reject stale callbacks
after navigation, logout, or session changes. Use Timeline only for short UI
updates such as a countdown display; assessment timing rules belong in the
service layer.

## Shared visual design

Use the palette in [System UI Rules](System%20UI%20Rules.md.md).
Centralize colors, fonts, spacing, and borders in a shared `theme.css`
stylesheet under Ui/Theme/, using looked-up color variables on `.root`. Keep
Java-side constants in theme.java only where code needs them (for example,
chart series colors). Reuse styled controls under Ui/Components/. Fonts, icons,
and stylesheets load from classpath resources, not absolute filesystem paths.
Bundle Satoshi fonts before using them and fall back to a system font when
unavailable.

Implement hover, pressed, selected, disabled, error, and visible keyboard focus
states with CSS pseudo-classes (:hover, :pressed, :selected, :disabled,
:focused) and custom style classes or PseudoClass states for errors. Use the
built-in javafx.scene.chart controls (BarChart, PieChart, LineChart) for
dashboard charts, wrapped in shared chart components under Ui/Components/.

## Sizing, accessibility, and validation

Authentication uses the documented 1000 x 500 window (non-resizable Stage). Role
dashboards are resizable, open at up to 1440 x 900 capped at 92% of the
screen's visual bounds (Screen.getPrimary().getVisualBounds()), and have a
900 x 650 minimum (Stage.setMinWidth/setMinHeight) reduced for smaller screens.
Use ScrollPane and deliberate card/chart reflow to keep content accessible.

Associate labels with inputs using Label.setLabelFor(), give controls
accessible text (setAccessibleText), and maintain logical focus traversal
through node order. Preserve visible keyboard focus, provide mnemonics
(mnemonicParsing with an underscore in the label text) and default/cancel
buttons (setDefaultButton/setCancelButton), and display field-level validation
without relying on color alone. Read PasswordField input only when submitting,
pass it straight to the service, and clear the field after use.

## Organization and references

Follow [Recommended Folder Structure](../System/Recommended%20Folder%20Structure.md).
Follow [Database Rules](Database%20Rules.md.md) for persistence boundaries.

JavaFX is not bundled with the JDK. Add an OpenJFX SDK matching the project's
Java version through NetBeans Libraries, and run with the JavaFX modules on the
module path (`--module-path <javafx-sdk>/lib --add-modules javafx.controls,javafx.fxml`).

The concurrency and navigation rules follow the
[OpenJFX documentation](https://openjfx.io/) and Oracle's
[JavaFX concurrency guidance](https://docs.oracle.com/javase/8/javafx/interoperability-tutorial/concurrency.htm).
