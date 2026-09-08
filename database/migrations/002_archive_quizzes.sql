-- Additive and retry-safe. Existing quizzes retain their status and history.
USE quiz_application_system;
SET @quiz_archive_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quizzes' AND COLUMN_NAME='archived_at'
);
SET @quiz_archive_sql = IF(@quiz_archive_exists=0,
    'ALTER TABLE quizzes ADD COLUMN archived_at TIMESTAMP NULL DEFAULT NULL AFTER status',
    'SELECT ''quizzes.archived_at already exists'' AS migration_status');
PREPARE quiz_archive_statement FROM @quiz_archive_sql;
EXECUTE quiz_archive_statement;
DEALLOCATE PREPARE quiz_archive_statement;
-- Application rollback: deploy the previous application and leave this nullable
-- column in place. Archived quizzes also have status='closed', so old readers
-- cannot mistake them for published quizzes. No destructive rollback is needed.
