# Role Panel Implementation Scope

## Scope analysis

The project overview defines a JavaFX desktop quiz system with three roles.
Administrators manage users, quizzes, subjects, accounts, reports, and results.
Teachers prepare quizzes and monitor student performance. Students take quizzes
and review their own results. The architecture separates presentation, application
logic, and database access.

This delivery implements role panels, MySQL login, and the live admin overview.
Each panel uses a resizable window that initially fits the screen, a role-specific sidebar,
Satoshi typography, and the documented brand palette.

| Role | Sidebar destinations, followed by Logout |
| --- | --- |
| Administrator | Dashboard, Student Management, Teacher Management, Quiz Management, Subject/Category Management, Reports, Results, Account Management |
| Teacher | Dashboard, Create Quiz, Assigned Subjects, Student Results, Quiz Statistics, Profile |
| Student | Dashboard, Available Quizzes, Take Quiz, Quiz Results, Profile |

## Delivered behavior

- Dashboard is selected initially; clicking a destination selects that item.
- Admin Dashboard displays six KPI cards and three charts using MySQL data.
- Student Management displays status KPIs, searchable/filterable records, Edit,
  and confirmed Archive actions. Other admin destinations and teacher/student workspaces remain blank.
- MySQL login validates credentials and active status, then routes by account role.
- Logout clears the in-memory session and returns to the 1000 x 500 login screen.
- Navigation controls provide hover, pressed, selected, and keyboard focus states.
- Each role has a dedicated FXML view and shares dashboard.css and PanelController.

The existing studentDashbaord.fxml filename is retained for compatibility.

## Deferred functionality

The admin overview verifies active administrator access. Other feature authorization,
CRUD forms, quiz attempts, grading, reports, profile
editing, registration, and password reset remain future work.
The preview launcher is a development tool and does not authenticate users.
The normal login route uses a verified session from the application layer.
The preview launcher displays layouts without fetching admin data or granting a session.
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
admin overview visibility and blank feature pages, and exercised logout for each role.
AdminDashboardDataTest checks aggregates with rolled-back fixtures.
AdminDashboardViewTest checks empty/populated states and produces a visual fixture
snapshot. LoginSmokeTest verifies live admin loading and all three resizable
role routes, initial screen fit, and login sizing reset. Responsive view tests verify
card/chart reflow. The final runs reported no FXML or CSS errors.
