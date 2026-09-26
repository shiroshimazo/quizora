# Administrator dashboard

> Status: Planned JavaFX feature specification. Named classes and reuse points below are design targets, not verified existing code.

The Dashboard destination provides a read-only system overview from SQLite.
All three role panels are resizable and support maximizing. They open at up to
1440 x 900 outer-window dimensions, capped at 92% of the current screen's work area.
Minimum dimensions are 900 x 650, reduced if necessary to fit a smaller screen.
Authentication remains 1000 x 500. The administrator overview scrolls vertically.

| Component | Definition and purpose |
| --- | --- |
| Students KPI | All users whose role is student, including inactive accounts |
| Teachers KPI | All users whose role is teacher, including inactive accounts |
| Quizzes KPI | All quizzes, across publication states |
| Subjects KPI | All subjects |
| Submitted attempts KPI | All submitted attempts, including repeat attempts |
| Average score KPI | Arithmetic mean of each finalized submitted attempt's score / total_points, as a percentage |
| Bar chart | Top eight subjects by quiz count, across publication states; ties sort by subject name |
| Line chart | Daily submitted attempts for the last 14 calendar dates, including today; missing dates are zero |
| Pie chart | All quizzes grouped as Draft, Published, or Closed |

Average score displays a dash when there are no eligible results. It is not a
pass rate: the current scope does not define a passing threshold.
The line chart groups submission timestamps using local-time dates stored in SQLite.
No sample performance values are displayed in the running app.

## Data and interaction

AdminDashboardDAO validates the current identity against an active admin account
before reading. A read-only repeatable-read transaction keeps the aggregates
consistent. Queries run in a background javafx.concurrent.Task, with ten-second statement
timeouts. Refresh reloads the entire snapshot and shows an updated time.
Loading, unavailable-data, and no-data states are explicit. Failed refreshes clear
old values so they cannot be mistaken for current values.

Sidebar navigation hides the overview on unfinished destinations and restores it
when Dashboard is selected. Logout retains the existing confirmation dialog.
Teacher and student logins do not load the administrator overview.
The development preview displays an administrator sign-in message instead of querying data.

The planned view uses JavaFX layout panes, Satoshi fonts, and the shared theme.css
palette. Charts use the built-in javafx.scene.chart BarChart and PieChart,
wrapped in reusable components under Ui/Components/. Cards use one column below
600 pixels of workspace viewport width, two below 1050, and three above.
Activity spans the workspace width. Bar/pie charts stack below 1100 viewport pixels
and share a row above that width.
Charts have labeled axes or a legend, count tooltips (Tooltip.install on each data node), and disabled data animation (setAnimated(false)).

## Planned verification

Implementation and tests are not present in this workspace. Before marking this
feature complete, verify authorization, data calculations, empty/error states,
JavaFX interactions on the JavaFX Application Thread, responsive layout, and Navigation/logout. Use
isolated database fixtures and confirm historical records are preserved.
