-- Add conversation grounding route fields for existing databases.
-- This script is idempotent for MySQL 8.x.

SET @schema_name = DATABASE();

SET @grounding_policy_exists = (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 'tb_context_trace'
       AND COLUMN_NAME = 'grounding_policy'
);
SET @ddl = IF(
    @grounding_policy_exists = 0,
    'ALTER TABLE tb_context_trace ADD COLUMN grounding_policy VARCHAR(32) DEFAULT NULL COMMENT ''Grounding policy for this answer'' AFTER context_mode',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @route_decision_exists = (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 'tb_context_trace'
       AND COLUMN_NAME = 'route_decision'
);
SET @ddl = IF(
    @route_decision_exists = 0,
    'ALTER TABLE tb_context_trace ADD COLUMN route_decision VARCHAR(64) DEFAULT NULL COMMENT ''Resolved answer route'' AFTER grounding_policy',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @llm_called_exists = (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 'tb_context_trace'
       AND COLUMN_NAME = 'llm_called'
);
SET @ddl = IF(
    @llm_called_exists = 0,
    'ALTER TABLE tb_context_trace ADD COLUMN llm_called TINYINT(1) DEFAULT NULL COMMENT ''Whether model provider was called'' AFTER route_decision',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
