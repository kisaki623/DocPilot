-- Add soft-delete state for document library delete action.
-- Existing rows remain visible through the ACTIVE default.

SET @document_status_column_exists = (
    SELECT COUNT(1)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'tb_document'
       AND COLUMN_NAME = 'status'
);

SET @document_status_column_sql = IF(
    @document_status_column_exists = 0,
    'ALTER TABLE tb_document ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT ''ACTIVE'' COMMENT ''Document status: ACTIVE, REMOVED'' AFTER parse_status',
    'SELECT 1'
);

PREPARE document_status_column_stmt FROM @document_status_column_sql;
EXECUTE document_status_column_stmt;
DEALLOCATE PREPARE document_status_column_stmt;

SET @document_status_index_exists = (
    SELECT COUNT(1)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'tb_document'
       AND INDEX_NAME = 'idx_document_status'
);

SET @document_status_index_sql = IF(
    @document_status_index_exists = 0,
    'CREATE INDEX idx_document_status ON tb_document (status)',
    'SELECT 1'
);

PREPARE document_status_index_stmt FROM @document_status_index_sql;
EXECUTE document_status_index_stmt;
DEALLOCATE PREPARE document_status_index_stmt;
