# Teacher Quiz Statistics

> Status: Planned JavaFX feature specification. Named classes and reuse points below are design targets, not verified existing code.

The Quiz Statistics sidebar opens an all-time comparison of the signed-in teacher's quizzes, including clearly labeled archived quizzes. Choose a quiz or All quizzes; the selection applies to KPI cards, charts and the comparison table. Refresh preserves the selected quiz if it still belongs to the teacher.

- Attempts includes in-progress and submitted attempts, with retakes counted separately.
- Submitted includes unscored submitted attempts.
- Completion rate is submitted attempts divided by all attempts; no attempts displays N/A.
- Average score is the mean finalized submitted attempt percentage. The overall average weights each quiz average by its scored attempt count. No scored attempts displays N/A.
- The bar chart compares average scores for up to eight quizzes with the most scored submissions. IDs distinguish duplicate titles; tooltips show the title, score and scored count.
- The pie chart separates submitted and in-progress attempts.
- The line chart shows the last 14 database-calendar days, including zero days. It uses the selected quiz scope and excludes dates outside the window.
- The sortable table retains quizzes without attempts and includes status, attempt/submission/scored counts and average score.

QuizStatisticsDAO verifies active, non-archived teacher access and reads a consistent transaction snapshot. Database work runs off the JavaFX Application Thread. Refresh clears previous data, controls are disabled while loading, and callbacks reject session changes. No schema changes are required.

## Planned verification

No implementation or test suite for this feature is present in this workspace.
Verify the behavior above with Service/DAO tests and JavaFX UI checks, including
role isolation, session changes, loading/error states, and responsive layout.
