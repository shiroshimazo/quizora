# Sidebar Navigation Panel

The Sidebar Navigation Panel serves as the main navigation area after user login.

The sidebar will be a JavaFX VBox in the left region of the dashboard BorderPane, containing navigation ToggleButtons in a ToggleGroup and styled through the shared theme.css stylesheet. Its controller exposes destinations for the signed-in role and switches workspace views by replacing the content of the central StackPane. Services must independently enforce authorization.

The system consists of three user panels:

## 1. Administrator Panel

### Navigation Menu:
- Dashboard
- Student Management
- Teacher Management
- Quiz Management
- Subject/Category Management
- Reports
- Results
- Account Management
- Logout

### Panel Size:
- **Resizable; opens up to 1440x900 pixels within the screen work area**

---

## 2. Teacher Panel

### Navigation Menu:
- Dashboard
- Create Quiz
- Assigned Subjects
- Student Results
- Quiz Statistics
- Profile
- Logout

### Panel Size:
- **Resizable; opens up to 1440x900 pixels within the screen work area**

---

## 3. Student Panel

### Navigation Menu:
- Dashboard
- Available Quizzes
- Take Quiz
- Quiz Results
- Profile
- Logout

### Panel Size:
- **Resizable; opens up to 1440x900 pixels within the screen work area**
