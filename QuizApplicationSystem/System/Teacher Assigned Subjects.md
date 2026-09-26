# Teacher Assigned Subjects

> Status: Planned JavaFX feature specification. Named classes and reuse points below are design targets, not verified existing code.

The teacher sidebar now opens a live Assigned Subjects directory. It displays current, non-archived assignments, category and description, and the teacher's own non-archived quiz and published-quiz counts. Subjects without quizzes remain visible.

Three KPI cards summarize all current assignments. Search filters subject ID, name, category and description without changing the KPI scope. Selecting a row displays its full description. Refresh reloads assignments asynchronously, and errors/session changes clear unavailable data. Empty assignments explain that an administrator must assign subjects. This page does not grant teachers permission to assign themselves subjects.

AssignedSubjectsDAO checks active teacher access using the existing teacher authorization check and reads assignment data in a consistent transaction. No database schema changes are needed.

## Planned verification

No implementation or test suite for this feature is present in this workspace.
Verify the behavior above with Service/DAO tests and JavaFX UI checks, including
role isolation, session changes, loading/error states, and responsive layout.
