-- DocPilot P0 core tables initialization
-- Scope: tb_user, tb_file_record, tb_document, tb_parse_task

CREATE TABLE IF NOT EXISTS tb_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    username VARCHAR(64) NOT NULL COMMENT 'Unique username for login',
    password_hash VARCHAR(128) NOT NULL COMMENT 'Password hash',
    phone VARCHAR(32) DEFAULT NULL COMMENT 'Optional phone number',
    nickname VARCHAR(64) DEFAULT NULL COMMENT 'Display name',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'User status: ACTIVE, DISABLED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='User table';

CREATE TABLE IF NOT EXISTS tb_file_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    file_name VARCHAR(255) NOT NULL COMMENT 'Original file name',
    file_ext VARCHAR(16) NOT NULL COMMENT 'File extension: pdf, md, txt',
    content_type VARCHAR(128) DEFAULT NULL COMMENT 'MIME content type',
    file_size BIGINT UNSIGNED NOT NULL COMMENT 'File size in bytes',
    storage_path VARCHAR(512) NOT NULL COMMENT 'Stored file path (local path first)',
    file_hash VARCHAR(64) DEFAULT NULL COMMENT 'Optional SHA-256 hash for future dedup',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    KEY idx_file_record_user_id (user_id),
    KEY idx_file_record_hash (file_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Uploaded file record table';

CREATE TABLE IF NOT EXISTS tb_document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    file_record_id BIGINT UNSIGNED NOT NULL COMMENT 'Related uploaded file id',
    title VARCHAR(255) NOT NULL COMMENT 'Document title',
    summary TEXT COMMENT 'Parsed summary',
    content LONGTEXT COMMENT 'Parsed full content',
    parse_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Parse status: PENDING, UPLOADED, PARSING, SPLITTING, SUMMARIZING, INDEXING, SUCCESS, FAILED (兼容 PROCESSING)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    KEY idx_document_user_id (user_id),
    KEY idx_document_file_record_id (file_record_id),
    KEY idx_document_parse_status (parse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Document table';

CREATE TABLE IF NOT EXISTS tb_parse_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    document_id BIGINT UNSIGNED NOT NULL COMMENT 'Related document id',
    file_record_id BIGINT UNSIGNED NOT NULL COMMENT 'Related uploaded file id',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Task status: PENDING, UPLOADED, PARSING, SPLITTING, SUMMARIZING, INDEXING, SUCCESS, FAILED (兼容 PROCESSING)',
    error_msg VARCHAR(512) DEFAULT NULL COMMENT 'Failure reason when status is FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT 'Retry count for future retry strategy',
    start_time DATETIME DEFAULT NULL COMMENT 'Task start time',
    finish_time DATETIME DEFAULT NULL COMMENT 'Task finish time',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    KEY idx_parse_task_user_id (user_id),
    KEY idx_parse_task_document_id (document_id),
    KEY idx_parse_task_file_record_id (file_record_id),
    KEY idx_parse_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Document parse task table';

