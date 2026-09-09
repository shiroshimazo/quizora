# Teacher Student Results

The Student Results sidebar opens a teacher-scoped view of finalized scored submissions. Only attempts on quizzes owned by the authenticated teacher are returned. Retakes remain separate rows. Archived quizzes, subjects, and students remain visible for historical results; unfinished and unscored attempts are excluded.

Search by student, username, quiz, subject or ID; filter by subject, outcome and inclusive submission dates. An adjustable 0-100% passing threshold defaults to 75% and changes this view only, never stored grades. Reset filters clears search, subject, outcome and dates while retaining the chosen threshold.

Four KPIs show filtered submission count, mean attempt percentage, pass rate and unique students. The score-distribution bar chart and pass/fail pie chart use the same filtered results. The sortable table includes attempt ID, student, username, quiz, subject, score, percentage, outcome and submitted date. Wide tables scroll horizontally within the table; cards and charts reflow on narrow windows.

Teacher results reuse the existing results presentation with a separate teacher-authorized DAO entry point. Reads run asynchronously and verify active, non-archived teacher access. Session changes and failed refreshes clear result records. No database schema changes are required.

Verification: Ant compile, TeacherResultsTest, ResultsTest, TeacherResultsViewTest, ResultsViewTest and PanelSmokeTest passed. Database fixtures are rolled back. The teacher-results-preview.png in build uses illustrative test data.
