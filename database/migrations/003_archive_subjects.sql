-- Additive and retry-safe. Existing subjects retain their status and history.
USE quiz_application_system;
SET @subject_archive_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='subjects' AND COLUMN_NAME='archived_at'
);
SET @subject_archive_sql = IF(@subject_archive_exists=0,
    'ALTER TABLE subjects ADD COLUMN archived_at TIMESTAMP NULL DEFAULT NULL AFTER description',
    'SELECT ''subjects.archived_at already exists'' AS migration_status');
PREPARE subject_archive_statement FROM @subject_archive_sql;
EXECUTE subject_archive_statement;
DEALLOCATE PREPARE subject_archive_statement;
-- Application rollback: leave the nullable column and all data in place.
-- Older quiz editors do not enforce subject archive eligibility; use the updated
-- application for writes while archived subjects exist.
