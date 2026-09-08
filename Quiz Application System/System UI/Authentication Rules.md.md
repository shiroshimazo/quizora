# Authentication UI Rules

The authentication interfaces use the same split-screen layout for consistency.

Authentication screens are JavaFX views defined in FXML and styled with the shared Quizora CSS stylesheet. Their controllers handle validation, navigation, and authentication requests.

Authentication screens include:

- Login UI
- Register UI
- Forgot Password UI

### Panel Size:
- **1000x500 pixels**
---
# Authentication Layout Structure

The authentication window is divided into two main sections:

## Left Section: Branding Area

### Purpose:
Displays the visual identity of the system.

### Components:
- System logo
- Illustration/image
- Background design
- Branding elements

### Layout Rules:
- Occupies approximately 50% of the window width.
- Image/logo must be centered.
- Visual elements must not overlap with the form section.

---

## Right Section: Form Area

### Purpose:
Contains user input and authentication controls.

### Components:

### Login UI:
- Username/Email field
- Password field
- Login button
- Forgot password link
- Register link

### Register UI:
- Full name field
- Username field
- Email field
- Password field
- Confirm password field
- Register button
- Back to login link

### Forgot Password UI:
- Username/Email field
- Verification input
- Reset password option
- Submit button
- Back to login link

### Layout Rules:
- Form elements must be vertically aligned.
- Labels and input fields must have consistent spacing.
- Buttons must use the same design style across all authentication pages.
