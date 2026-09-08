# Student Management

The administrator's Student Management destination provides a read-only directory
with explicit Edit and Archive actions. Only student-role accounts are listed.

## KPI definitions

| Card | Definition |
| --- | --- |
| Total Student | All student records, including archived accounts |
| Active Student | Students with is_active = TRUE and archived_at IS NULL |
| Inactive Student | Students with is_active = FALSE and archived_at IS NULL |

Archived students are separate from inactive students. Total equals active plus
inactive plus archived. Search and status filters do not change the KPI totals.

## Directory

Columns: ID, Name, Username, Email, Status, Actions.

Search matches a literal, case-insensitive substring of ID, name, username, or email.
It combines with All statuses, Active, Inactive, and Archived filters.
Column headers support sorting, except Actions. The footer shows filtered and
overall counts. Empty records, no matching records, loading, and unavailable-data
states have distinct messages. Refresh retrieves current data and preserves filters.

The initial implementation loads student projections without credentials and filters
them locally. Server-side pagination/search is deferred until dataset size requires it.

## Editing and archiving

Edit opens a modal form for name, username, email, and Active/Inactive status.
Names and usernames are required; schema length limits and email format are checked.
Usernames cannot contain whitespace. Duplicate usernames/emails are rejected across
all roles and archived accounts, including username/email cross-field collisions.
Password, role, ID, and assessment records are not editable here.

Archive requires confirmation. It sets archived_at and disables is_active without
deleting the user, attempts, answers, or results. Archived students stay in the
directory, have disabled actions, and cannot log in. Restore/unarchive and account
creation are outside this delivery.

Reads and writes verify an active, unarchived administrator in MySQL. Writes lock the
admin and student records, compare the original editable fields/state to prevent
stale overwrites, and commit atomically. Database work runs off the JavaFX UI thread.
Actions are disabled while requests run. Failures remain visible and editable forms
stay open for correction or cancellation.

## Layout

KPI cards reflow into a single column on narrow workspaces. Search/filter controls
wrap. The table keeps its headers and action columns, enabling horizontal scrolling
when minimum column widths cannot fit. The whole page scrolls vertically when needed.
Row statuses use text as well as color, and clipped identity fields have tooltips.

The implementation follows the [JavaFX TableView resize policies](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/TableView.html).

## Database migration

Fresh installations include users.archived_at in database/quiz_application_system.sql.
Existing installations must run database/migrations/001_archive_users.sql before
running the updated application. The migration checks column existence and can be
rerun. It adds only a nullable timestamp and does not change existing fields or rows.

The local migration was applied successfully. Before/after verification showed three
user records with the same fingerprint across all original fields. A second run was
a no-op. After integration fixtures were removed, the same fingerprint was verified.

For application rollback, leave the additive column in place. Archived accounts keep
is_active = FALSE, which the previous login implementation understands. Do not drop
the column or reactivate archived accounts during rollback: that would discard archive
state. No destructive schema rollback is provided or executed.

## Verification

- Ant jar build passed.
- StudentManagementTest passed for authorization, student-only reads, filters,
  validation, duplicate identities, saved edits, stale writes, archival, login
  restrictions, and preserved historical quiz results.
- StudentManagementViewTest exercised real JavaFX search/filter controls, stable
  KPI totals, edit validation/save/cancel, archive confirmation/cancel, disabled
  archived actions, and responsive layout.
- PanelSmokeTest checked all 19 destinations and logout; LoginSmokeTest verified
  all three role logins after the additive migration.
- Test users and assessment fixtures were removed using their generated IDs.
  The original accounts were unchanged.

The inspected build/student-management-preview.png screenshot contains a labeled
test student from the UI check; that fixture is not retained in MySQL.
Forced database-outage and concurrent multi-client stress tests remain unperformed.
