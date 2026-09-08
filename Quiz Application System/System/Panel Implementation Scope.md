# Role Panel Implementation Scope

## Scope analysis

The project overview defines a JavaFX desktop quiz system with three roles.
Administrators manage users, quizzes, subjects, accounts, reports, and results.
Teachers prepare quizzes and monitor student performance. Students take quizzes
and review their own results. The architecture separates presentation, application
logic, and database access.

This delivery implements presentation shells and MySQL login for those roles.
Each panel uses the documented fixed 1180 x 700 size, a role-specific sidebar,
Satoshi typography, and the documented brand palette.

| Role | Sidebar destinations, followed by Logout |
| --- | --- |
| Administrator | Dashboard, Student Management, Teacher Management, Quiz Management, Subject/Category Management, Reports, Results, Account Management |
| Teacher | Dashboard, Create Quiz, Assigned Subjects, Student Results, Quiz Statistics, Profile |
| Student | Dashboard, Available Quizzes, Take Quiz, Quiz Results, Profile |

## Delivered behavior

- Dashboard is selected initially; clicking a destination selects that item.
- Every destination leaves the main workspace completely blank.
- MySQL login validates credentials and active status, then routes by account role.
- Logout clears the in-memory session and returns to the 1000 x 500 login screen.
- Navigation controls provide hover, pressed, selected, and keyboard focus states.
- Each role has a dedicated FXML view and shares dashboard.css and PanelController.

The existing studentDashbaord.fxml filename is retained for compatibility.

## Deferred functionality

Feature-level authorization, CRUD forms, quiz attempts, grading, reports, profile
editing, registration, and password reset remain future work.
The preview launcher is a development tool and does not authenticate users.
The normal login route uses a verified session from the application layer.
The preview launcher only displays blank layouts and does not grant a session.
Existing database structures are unchanged.

## Preview

In NetBeans, use quizora.dashboard.PanelPreview as the main class in a separate
run configuration, with one argument: admin, teacher, or student. No argument
opens the administrator preview. Keep quizora.Quizora as the normal login entry.

With Java and Ant configured, a command-line preview is:

```text
ant -Dmain.class=quizora.dashboard.PanelPreview -Dapplication.args=teacher run
```

## Validation

The Ant jar build passed. PanelSmokeTest loaded and laid out all three FXML views,
checked all 19 navigation destinations (including repeated selection), verified
empty workspaces, and exercised logout for each role. The JavaFX runtime reported
no FXML or CSS errors. Manual visual and keyboard traversal checks remain pending.
