# Teacher Profile

The teacher Profile sidebar shows the signed-in teacher's name, username, email, contact number, picture, role and membership date. Edit profile opens the existing validated modal; Cancel leaves the database unchanged. Successful edits refresh the displayed profile and session name while preserving the teacher role.

Change picture accepts PNG/JPEG images up to 2 MB and 4096 by 4096 pixels. File reading and validation run asynchronously. A preview must be saved before the picture changes. The existing ProfilePicture validator and profile persistence are reused.

TeacherProfileDAO enforces active, non-archived teacher access for reads and writes and locks the account during writes. Updates are limited to the authenticated account, reject stale profiles and duplicate usernames/emails, and preserve password and role. Controller callbacks reject session changes. No database migration is needed.

Verification: Ant compile; TeacherProfileTest covers own-account authorization, inactive/archived/changed-role rejection, duplicate and stale changes, image validation and persistence, unchanged login, modal validation/save/cancel, session role/name preservation and responsive widths. AccountProfileTest verifies the administrator workflow remains functional; PanelSmokeTest verifies sidebar navigation and logout. Temporary profile test accounts are removed after testing.
