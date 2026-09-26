# Role Panel Implementation Scope

## Current status

This workspace contains a NetBeans/Ant project with an empty
`QuizoraApp.quizoraApp.main` method. No role panels, authentication implementation,
preview launcher, database scripts, or test suites are present. This document
defines planned JavaFX behavior; earlier delivery and passing-test claims do not
establish implementation in this workspace.

## Planned navigation

| Role | Sidebar destinations, followed by Logout |
| --- | --- |
| Administrator | Dashboard, Student Management, Teacher Management, Quiz Management, Subject/Category Management, Reports, Results, Account Management |
| Teacher | Dashboard, Create Quiz, Assigned Subjects, Student Results, Quiz Statistics, Profile |
| Student | Dashboard, Available Quizzes, Take Quiz, Quiz Results, Profile |

Use one appWindow (primary Stage) that switches between the authentication and
dashboard roots. dashboardView is a BorderPane hosting sidebarView and a
StackPane workspace with role-specific JavaFX views.
Use the shared theme.css stylesheet, Satoshi fonts once bundled, and documented
sizing. Modal forms use owned Dialog/Alert windows or a secondary Stage with
initOwner and window modality. Optional FXML files live beside their matching
controller classes.

## Planned behavior

- Authenticate active accounts through a service and route using the verified role.
- Select Dashboard initially and expose only the role's permitted destinations.
- Enforce authorization in services/data access as well as navigation.
- Provide loading, empty, failure, and populated states for data screens.
- Preserve unfinished quiz drafts during workspace navigation.
- Confirm logout, clear session and protected screen data, reject pending worker
  callbacks, and return to the 1000 x 500 authentication window.
- Use javafx.concurrent.Task for database work and update controls on the
  JavaFX Application Thread.

The feature documents define target behavior. Registration, password recovery,
remaining administrator management pages, and the student assessment flow need
implementation and their own focused acceptance checks.

## Entry point and verification

Keep `QuizoraApp.quizoraApp` as the configured entry point. No preview command is
available yet. After implementation, run the Ant build and verify all 19 sidebar
destinations, each role's login and logout, denied access, session cleanup,
responsive layouts, keyboard navigation, and database failure states.
Do not report those checks as passing until they have actually run.

See [Recommended Folder Structure](Recommended%20Folder%20Structure.md).
