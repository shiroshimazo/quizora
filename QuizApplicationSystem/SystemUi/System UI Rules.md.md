# System UI Rules

## General UI Guidelines

The Quiz Application System follows a consistent user interface design to provide a simple, organized, and user-friendly experience for administrators, teachers, and students.

The desktop interface will use JavaFX. FXML views (or view classes) and layout panes define screens; a shared CSS stylesheet defines visual styling. Presentation controllers coordinate interaction and state.

### General Rules:
- All authentication interfaces must use a fixed resolution of **1000x500 pixels**.
- All user dashboard panels are **resizable**, opening at up to **1440x900 pixels**, capped at 92% of the current screen work area. Dashboard content must reflow and scroll as needed.
- The interface must maintain consistent spacing, alignment, colors, and button styles.
- Navigation elements must be easy to understand and accessible.
- The design must support clear separation between different user roles.

# UI Consistency Rules

## Colors
- Use a consistent color palette throughout all screens.
- Primary colors should be used for buttons and important actions.
- Warning colors should only be used for errors or destructive actions.

## Brand Colors
Define these values as looked-up color variables on `.root` in `Ui/Theme/theme.css`
(for example `-primary: #A8DADC;`). Apply the stylesheet to every Scene and use
style classes for reusable component styling. Mirror them as `javafx.scene.paint.Color`
constants in `Ui/Theme/theme.java` only where code needs them.

| Token | Hex |
| --- | --- |
| Background main | #F7FAFC |
| Surface | #FFFFFF |
| Background secondary | #EDF4F8 |
| Primary | #A8DADC |
| Primary hover | #82C4C7 |
| Accent | #BDE0FE |
| Text primary | #253238 |
| Text secondary | #68777D |
| Border | #DCE5E8 |
| Success | #CDEDD6 |
| Warning | #FAEDCD |
| Error | #F6CACA |

## Typography
- Bundle Satoshi font files under `Source/QuizoraApp/Resources/Fonts/Satoshi/` (assets are not yet present). Load them with `Font.loadFont` before the first Scene is shown (or `@font-face` in `theme.css`), set sizes in CSS, and provide a system-font fallback in `-fx-font-family`.
- Satoshi-Bold: For headings
- Satoshi-Medium: For subheadings
- Satoshi-Regular: For paragraphs
- Maintain consistent font sizes for:
  - Titles
  - Labels
  - Buttons
  - Navigation items
## Buttons
- All buttons must have:
  - Clear text labels
  - Consistent size
  - Consistent placement
  - Hover and click feedback
## Forms
- Required fields must be clearly identified.
- Input validation messages must be displayed clearly.
- Invalid inputs must provide user feedback.

---

[[Authentication Rules.md]]
[[Sidebar Nav Ui.md]]
