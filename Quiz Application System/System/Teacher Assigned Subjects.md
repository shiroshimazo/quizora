# Teacher Assigned Subjects

The teacher sidebar now opens a live Assigned Subjects directory. It displays current, non-archived assignments, category and description, and the teacher's own non-archived quiz and published-quiz counts. Subjects without quizzes remain visible.

Three KPI cards summarize all current assignments. Search filters subject ID, name, category and description without changing the KPI scope. Selecting a row displays its full description. Refresh reloads assignments asynchronously, and errors/session changes clear unavailable data. Empty assignments explain that an administrator must assign subjects. This page does not grant teachers permission to assign themselves subjects.

AssignedSubjectsDAO checks active teacher access using the existing teacher authorization check and reads assignment data in a consistent transaction. No database schema changes are needed.

Verification passed: Ant compile, AssignedSubjectsTest (rolled-back MySQL fixtures), AssignedSubjectsViewTest (search/details/empty states and widths 580, 900, 1300), and PanelSmokeTest (navigation and logout).
