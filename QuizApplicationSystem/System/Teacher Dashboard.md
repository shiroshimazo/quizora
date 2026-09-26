# Teacher dashboard

> Status: Planned JavaFX feature specification. Named classes and reuse points below are design targets, not verified existing code.

Planned for the JavaFX teacher workspace using the shared theme.css stylesheet and reusable chart components built on the javafx.scene.chart controls.

- KPIs: unique students with submitted attempts, published quizzes, total quizzes, current non-archived assigned subjects, submitted attempts, and mean finalized attempt percentage.
- Bar chart: top eight subjects by the teacher's quiz count.
- Circular pie chart: draft, published, and closed quiz counts.
- Line chart: submitted attempts per day over the last 14 database-calendar days, including zero days.

Quiz metrics exclude archived quizzes and are restricted to the signed-in teacher. Retakes count separately; student reach counts distinct students. Subjects count current assignments; quiz history remains included even if the subject assignment changes. No scores displays an em dash rather than a fabricated zero.

TeacherDashboardDAO verifies active, non-archived teacher access and reads a consistent transaction snapshot. The controller loads asynchronously, supports refresh and unavailable states, and rejects results after a session change. Cards reflow into one, two, or three columns; lower charts stack on narrow windows.

## Planned verification

No implementation or test suite for this feature is present in this workspace.
Verify the behavior above with Service/DAO tests and JavaFX UI checks, including
role isolation, session changes, loading/error states, and responsive layout.
