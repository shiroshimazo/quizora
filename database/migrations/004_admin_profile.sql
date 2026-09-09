-- Additive, retry-safe profile fields. Rollback: retain nullable fields and deploy the previous application.
USE quiz_application_system;
SET @profile_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='contact_number');
SET @profile_sql = IF(@profile_exists=0, 'ALTER TABLE users ADD COLUMN contact_number VARCHAR(50) NULL', 'SELECT 1');
PREPARE profile_statement FROM @profile_sql;
EXECUTE profile_statement;
DEALLOCATE PREPARE profile_statement;
SET @profile_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='profile_picture');
SET @profile_sql = IF(@profile_exists=0, 'ALTER TABLE users ADD COLUMN profile_picture MEDIUMBLOB NULL', 'SELECT 1');
PREPARE profile_statement FROM @profile_sql;
EXECUTE profile_statement;
DEALLOCATE PREPARE profile_statement;
