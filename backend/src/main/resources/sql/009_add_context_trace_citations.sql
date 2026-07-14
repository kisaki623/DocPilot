-- Add persisted conversation citation snapshots for existing databases.
-- This script is idempotent for MySQL 8.x.

SET @schema_name = DATABASE();

SET @citations_json_exists = (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 'tb_context_trace'
       AND COLUMN_NAME = 'citations_json'
);
SET @ddl = IF(
    @citations_json_exists = 0,
    'ALTER TABLE tb_context_trace ADD COLUMN citations_json TEXT COMMENT ''Knowledge-base citations as JSON'' AFTER document_hit_counts_json',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
