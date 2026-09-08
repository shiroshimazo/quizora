-- Additive, retry-safe migration. Existing account data and active flags are preserved.
USE quiz_application_system;
SET @archive_column_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'archived_at'
);
SET @archive_migration = IF(@archive_column_exists = 0,
    'ALTER TABLE users ADD COLUMN archived_at TIMESTAMP NULL DEFAULT NULL AFTER is_active',
    'SELECT ''archived_at already exists'' AS migration_status');
PREPARE archive_statement FROM @archive_migration;
EXECUTE archive_statement;
DEALLOCATE PREPARE archive_statement;
