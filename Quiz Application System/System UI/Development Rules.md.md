# JavaFX UI Development Rules

## Purpose

This document defines the development rules for creating the Quiz Application System user interface. All UI work must follow these standards so authentication and role dashboards remain consistent, accessible, and easy to maintain.

The visual system also follows the guidance in [[System UI Rules.md]].

---

# Framework and Technology Requirements

## Required UI Framework

The desktop interface must be built with:

- JavaFX controls and layouts
- FXML files for declarative view structure
- Java controllers for event handling and presentation state
- CSS for colors, typography, spacing, states, and responsive sizing
- The bundled Satoshi font faces from `/Resources/Fonts`

Keep the UI within the JavaFX toolkit and avoid mixing in a second UI framework. Keep database and business rules in the application/data layers rather than in FXML views.

---

# FXML Rules

Each screen should have a dedicated FXML document. Arrange controls to preserve a clear hierarchy.

Developers should:

- Give every interactive control an `fx:id` and an accessible text or label.
- Declare handlers with `onAction="#methodName"` and implement them in the controller.
- Keep layout in FXML; do not use absolute positioning for primary page structure.
- Use `HBox`, `VBox`, `BorderPane`, `GridPane`, `StackPane`, and `ScrollPane` for layout.
- Load shared stylesheets from `/Resources/CSS` instead of embedding style strings in controllers.

Preferred controls include `Label`, `Button`, `TextField`, `PasswordField`, `TableView`, `ScrollPane`, `ComboBox`, `CheckBox`, `RadioButton`, and `ListView`.

FXML should contain presentation structure and static copy only. Validation, navigation, asynchronous work, and state transitions belong in controllers.

---

# JavaFX Component Naming Rules

Use meaningful camelCase `fx:id` values that describe the component's purpose.

| JavaFX component | `fx:id` example |
|-|-|
| `Label` | `titleLabel` |
| `Button` | `loginButton` |
| `TextField` | `usernameField` |
| `PasswordField` | `passwordField` |
| `Pane` | `sidebarPane` |
| `TableView` | `studentTable` |
| `ComboBox` | `subjectComboBox` |
| `ScrollPane` | `quizScrollPane` |

Avoid generated names such as `button1`, `textField1`, or `pane1`.

---

# UI Component Placement Rules

Every control must remain inside its designated layout container. Authentication screens use the fixed 1000×500 split layout: branding on the left and the form on the right. Dashboard screens use a resizable shell that initially fits the current screen, with sidebar navigation and a role-specific workspace. Reflow dashboard cards/charts on width changes and allow vertical scrolling.

For each screen:

- Keep labels adjacent to their fields and preserve a consistent vertical rhythm.
- Keep primary actions visually distinct from secondary navigation actions.
- Use `GridPane` constraints or grow priorities instead of fixed coordinates.
- Ensure the layout remains usable when text wraps or validation messages appear.

---

# Controller and Event Rules

FXML controllers must implement `Initializable` when initialization requires injected controls. Use JavaFX event handlers and observable properties for UI state.

Example:

```java
@FXML
private void login(ActionEvent event) {
    // Validate fields, then delegate authentication to the service.
}
```

Do not put SQL queries in controllers or FXML. Long-running authentication and database operations must run off the JavaFX Application Thread and update controls with `Platform.runLater` or a JavaFX `Task`.

---

# CSS Design Rules

The shared stylesheet must define the Quizora palette and reusable classes for surfaces, cards, fields, buttons, navigation states, and validation feedback.

- Use the design tokens in [[System UI Rules.md]].
- Keep hover, focused, pressed, selected, disabled, and error states explicit.
- Prefer CSS classes and pseudo-classes over inline `setStyle` calls.
- Use consistent corner radii, spacing, and elevation across nested cards and controls.
- Use `-fx-font-family: "Satoshi"` with regular, medium, and bold weights as appropriate.
- Provide a visible focus indicator and sufficient contrast for error text.

Example stylesheet usage:

```java
scene.getStylesheets().add(
        getClass().getResource("/Resources/CSS/quizora.css").toExternalForm());
```

---

# Accessibility and Validation

- Give controls descriptive labels and accessible text; do not rely on placeholder text alone.
- Preserve keyboard traversal order with FXML declaration order and `focusTraversable` settings.
- Mark required fields in their labels and show validation messages beside the affected control.
- Add an `error` style class or pseudo-class without removing the field's readable label.
- Disable or show a busy state while asynchronous work is in progress, and restore focus after errors.

---

# UI File Structure

FXML is the source of truth for view structure, CSS is the source of truth for presentation, and controllers coordinate behavior:

```text
src/
├── Authentication/
│   ├── LoginController.java
│   ├── RegistrationController.java
│   └── ForgotPasswordController.java
├── Dashboard/
│   └── DashboardController.java
└── Resources/
    ├── FXML/
    │   ├── login.fxml
    │   ├── registration.fxml
    │   ├── forgot-password.fxml
    │   └── dashboard.fxml
    └── CSS/
        └── quizora.css
```

Keep role authorization and database access in the corresponding application and data classes; FXML controllers should only orchestrate view state and user interaction.

# Database Rules

[[Database Rules.md]]
