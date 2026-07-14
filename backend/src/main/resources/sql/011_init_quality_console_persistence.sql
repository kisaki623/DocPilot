-- Initialize persisted Agent Quality Console tables for existing databases.
-- This script is idempotent for MySQL 8.x.

SET @schema_name = DATABASE();

SET @internal_admin_exists = (
    SELECT COUNT(*)
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 'tb_user'
       AND COLUMN_NAME = 'is_internal_admin'
);
SET @ddl = IF(
    @internal_admin_exists = 0,
    'ALTER TABLE tb_user ADD COLUMN is_internal_admin TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''Internal admin flag for restricted console access'' AFTER status',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS tb_quality_run (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    marker VARCHAR(160) NOT NULL COMMENT 'Stable quality run marker',
    status VARCHAR(32) NOT NULL DEFAULT 'REVIEW' COMMENT 'Run status',
    environment VARCHAR(64) NOT NULL DEFAULT 'local' COMMENT 'Runtime environment label',
    data_source VARCHAR(64) NOT NULL DEFAULT 'artifact_import' COMMENT 'Persisted data source',
    source_root_key VARCHAR(160) NOT NULL COMMENT 'Whitelisted artifact root key',
    source_relative_path VARCHAR(512) NOT NULL COMMENT 'Artifact path relative to whitelisted root',
    source_sha256 CHAR(64) NOT NULL COMMENT 'SHA-256 of imported artifact file',
    artifact_name VARCHAR(512) NOT NULL COMMENT 'Display artifact name',
    artifact_updated_at DATETIME DEFAULT NULL COMMENT 'Artifact file update time',
    imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Import time',
    import_revision INT NOT NULL DEFAULT 1 COMMENT 'Revision for same marker re-imports',
    gate_count INT NOT NULL DEFAULT 0 COMMENT 'Gate count',
    failed_gate_count INT NOT NULL DEFAULT 0 COMMENT 'Failed gate count',
    review_gate_count INT NOT NULL DEFAULT 0 COMMENT 'Review gate count',
    eval_case_count INT NOT NULL DEFAULT 0 COMMENT 'Eval case count',
    trace_reference_count INT NOT NULL DEFAULT 0 COMMENT 'Trace reference count',
    prompt_tokens INT DEFAULT NULL COMMENT 'Prompt token count',
    completion_tokens INT DEFAULT NULL COMMENT 'Completion token count',
    total_tokens INT DEFAULT NULL COMMENT 'Total token count',
    estimated_cost DECIMAL(18,8) DEFAULT NULL COMMENT 'Estimated model cost',
    failure_buckets_json TEXT COMMENT 'Failure buckets JSON',
    review_buckets_json TEXT COMMENT 'Review buckets JSON',
    diagnostics_json TEXT COMMENT 'Sanitized diagnostics JSON',
    trace_references_json MEDIUMTEXT COMMENT 'Sanitized trace references JSON',
    artifact_missing TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether artifact was missing at import time',
    artifact_parse_failed TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether artifact parse failed',
    redaction_status VARCHAR(32) NOT NULL DEFAULT 'PASS' COMMENT 'Redaction scan status',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_run_marker (marker),
    UNIQUE KEY uk_quality_run_source_sha256 (source_sha256),
    KEY idx_quality_run_updated_at (artifact_updated_at),
    KEY idx_quality_run_imported_at (imported_at),
    KEY idx_quality_run_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Persisted quality run summary table';

CREATE TABLE IF NOT EXISTS tb_quality_run_gate (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    run_id BIGINT UNSIGNED NOT NULL COMMENT 'Quality run id',
    gate_name VARCHAR(160) NOT NULL COMMENT 'Gate name',
    status VARCHAR(32) NOT NULL DEFAULT 'REVIEW' COMMENT 'Gate status',
    passed TINYINT(1) DEFAULT NULL COMMENT 'Whether gate passed',
    metrics_json TEXT COMMENT 'Sanitized numeric metrics JSON',
    flags_json TEXT COMMENT 'Sanitized boolean flags JSON',
    failure_buckets_json TEXT COMMENT 'Failure buckets JSON',
    review_buckets_json TEXT COMMENT 'Review buckets JSON',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Stable display order',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    KEY idx_quality_gate_run_order (run_id, sort_order),
    KEY idx_quality_gate_name (gate_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Persisted quality gate summary table';

CREATE TABLE IF NOT EXISTS tb_quality_run_case (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    run_id BIGINT UNSIGNED NOT NULL COMMENT 'Quality run id',
    case_id VARCHAR(160) NOT NULL COMMENT 'Eval case id',
    case_type VARCHAR(96) NOT NULL DEFAULT 'agent_quality' COMMENT 'Eval case type',
    status VARCHAR(32) NOT NULL DEFAULT 'REVIEW' COMMENT 'Case status',
    passed TINYINT(1) DEFAULT NULL COMMENT 'Whether case passed',
    trace_id VARCHAR(160) DEFAULT NULL COMMENT 'Sanitized trace id',
    agent_run_id VARCHAR(160) DEFAULT NULL COMMENT 'Sanitized agent run id',
    metrics_json TEXT COMMENT 'Sanitized numeric metrics JSON',
    flags_json TEXT COMMENT 'Sanitized boolean flags JSON',
    failure_buckets_json TEXT COMMENT 'Failure buckets JSON',
    review_buckets_json TEXT COMMENT 'Review buckets JSON',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Stable display order',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    KEY idx_quality_case_run_order (run_id, sort_order),
    KEY idx_quality_case_id (case_id),
    KEY idx_quality_case_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Persisted quality eval case summary table';

CREATE TABLE IF NOT EXISTS tb_quality_import_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    source_type VARCHAR(64) NOT NULL DEFAULT 'artifact' COMMENT 'Import source type',
    source_root_key VARCHAR(160) NOT NULL COMMENT 'Whitelisted artifact root key',
    source_relative_path VARCHAR(512) NOT NULL COMMENT 'Artifact path relative to root',
    artifact_sha256 CHAR(64) DEFAULT NULL COMMENT 'Artifact SHA-256 when readable',
    marker VARCHAR(160) DEFAULT NULL COMMENT 'Run marker when known',
    status VARCHAR(32) NOT NULL COMMENT 'Import status',
    safe_message VARCHAR(512) DEFAULT NULL COMMENT 'Safe import result message',
    requested_by_user_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Internal admin user who requested import',
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Import start time',
    finished_at DATETIME DEFAULT NULL COMMENT 'Import finish time',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    KEY idx_quality_import_status (status),
    KEY idx_quality_import_marker (marker),
    KEY idx_quality_import_sha256 (artifact_sha256),
    KEY idx_quality_import_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Quality artifact import event table';
