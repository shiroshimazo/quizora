# Student dashboard

The Dashboard destination of the student panel. It is selected after login and shows
the dashboard summary from the Student Dashboard flowchart: available quizzes, recent
results, and average scores. It is read-only; the other sidebar destinations
(Available Quizzes, Take Quiz, Quiz Results, Profile) are separate screens.

Built with `student/overview.fxml` and `studentDashboardController`, included in the
student shell as `dashboardContent`. It uses the shared dashboard stylesheet, the same
KPI cards, chart cards and table style as the teacher dashboard, and the built-in
javafx.scene.chart controls.

- KPIs: available quizzes, available quizzes not yet attempted, different quizzes
  completed, submitted attempts, average score, and best score.
- Available quizzes table: the 6 most recently updated available quizzes with subject,
  teacher, question count, time limit, and the student's submitted attempts.
- Recent results table: the student's 5 latest finalized submissions with score,
  percentage, and submission time.
- Bar chart: average score percentage per subject, top 8 subjects by attempt count,
  on a fixed 0–100 axis.

A quiz is available when it is published, not archived, belongs to a non-archived
subject, and has at least one question. There is no enrollment table, so every
student sees every available quiz. Retakes count as separate submissions; "quizzes
completed" counts distinct quizzes. Scores use finalized submitted attempts
(`quiz_results` with total points above zero), including quizzes archived later.
No scores displays an em dash rather than a fabricated zero.

StudentDashboardDAO verifies an active, non-archived student and reads all figures in
one read-only transaction, restricted to the signed-in student. The controller loads
in a background Task, supports refresh and unavailable states, and ignores results
after a session change. Cards reflow into one, two, or three columns; the results
table and subject chart stack on narrow windows.

## Verification

Checked against a scratch SQLite database: empty state (zeros and dashes), seeded
quizzes and results (counts, 75.0% average, 100.0% best, newest-first results),
teacher access rejected, and the rendered panel at 1440 x 900 and 1000 x 700.
There is no automated test suite in this workspace.
