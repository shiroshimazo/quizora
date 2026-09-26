# Teacher Profile

> Status: Planned JavaFX feature specification. Named classes and reuse points below are design targets, not verified existing code.

The teacher Profile sidebar shows the signed-in teacher's name, username, email, contact number, picture, role and membership date. Edit profile opens the existing validated modal; Cancel leaves the database unchanged. Successful edits refresh the displayed profile and session name while preserving the teacher role.

Change picture accepts PNG/JPEG images up to 2 MB and 4096 by 4096 pixels. File reading and validation run asynchronously. A preview must be saved before the picture changes. The existing ProfilePicture validator and profile persistence are reused.

TeacherProfileDAO enforces active, non-archived teacher access for reads and writes and locks the account during writes. Updates are limited to the authenticated account, reject stale profiles and duplicate usernames/emails, and preserve password and role. Controller callbacks reject session changes. No database migration is needed.

## Planned verification

No implementation or test suite for this feature is present in this workspace.
Verify the behavior above with Service/DAO tests and JavaFX UI checks, including
role isolation, session changes, loading/error states, and responsive layout.
