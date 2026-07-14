-- Add safe context trace technical details for existing databases.
-- This script is idempotent for MySQL 8.x.

SET @schema_name = DATABASE();

SET @technical_details_json_exists = (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 'tb_context_trace'
       AND COLUMN_NAME = 'technical_details_json'
);
SET @ddl = IF(
    @technical_details_json_exists = 0,
    'ALTER TABLE tb_context_trace ADD COLUMN technical_details_json TEXT COMMENT ''Safe context trace technical details as JSON'' AFTER citations_json',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
