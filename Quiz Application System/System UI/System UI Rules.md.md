# System UI Rules

## General UI Guidelines

The Quiz Application System follows a consistent user interface design to provide a simple, organized, and user-friendly experience for administrators, teachers, and students.

The desktop interface is implemented with JavaFX. FXML is used for declarative layouts and CSS is used for the shared visual design system; controllers coordinate interaction and presentation state.

### General Rules:
- All authentication interfaces must use a fixed resolution of **1000x500 pixels**.
- All user dashboard panels must use a fixed resolution of **1180x700 pixels**.
- The interface must maintain consistent spacing, alignment, colors, and button styles.
- Navigation elements must be easy to understand and accessible.
- The design must support clear separation between different user roles.

# UI Consistency Rules

## Colors
- Use a consistent color palette throughout all screens.
- Primary colors should be used for buttons and important actions.
- Warning colors should only be used for errors or destructive actions.

## Brand Colors
.root {
  -color-bg-main: #F7FAFC;
  -color-surface: #FFFFFF;
  -color-bg-secondary: #EDF4F8;

  -color-primary: #A8DADC;
  -color-primary-hover: #82C4C7;
  -color-accent: #BDE0FE;

  -color-text-primary: #253238;
  -color-text-secondary: #68777D;

  -color-border: #DCE5E8;

  -color-success: #CDEDD6;
  -color-warning: #FAEDCD;
  -color-error: #F6CACA;

  -fx-background-color: -color-bg-main;
}

## Typography
- Use Satoshi fonts in folder /Resources/Fonts/Satoshi
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
