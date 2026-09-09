# Teacher dashboard

Implemented in the existing JavaFX teacher shell using shared dashboard CSS and native charts.

- KPIs: unique students with submitted attempts, published quizzes, total quizzes, current non-archived assigned subjects, submitted attempts, and mean finalized attempt percentage.
- Bar chart: top eight subjects by the teacher's quiz count.
- Circular pie chart: draft, published, and closed quiz counts.
- Line chart: submitted attempts per day over the last 14 database-calendar days, including zero days.

Quiz metrics exclude archived quizzes and are restricted to the signed-in teacher. Retakes count separately; student reach counts distinct students. Subjects count current assignments; quiz history remains included even if the subject assignment changes. No scores displays an em dash rather than a fabricated zero.

TeacherDashboardDAO verifies active, non-archived teacher access and reads a consistent transaction snapshot. The controller loads asynchronously, supports refresh and unavailable states, and rejects results after a session change. Cards reflow into one, two, or three columns; lower charts stack on narrow windows.

Verification: TeacherDashboardDataTest uses rolled-back MySQL fixtures to check isolation, archives, totals, score normalization, and date boundaries. TeacherDashboardViewTest checks empty/populated rendering and responsive widths and creates build/teacher-dashboard-preview.png using explicitly labeled illustrative data. PanelSmokeTest checks navigation and logout.
