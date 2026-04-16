-- DocPilot stage 11.1 message reliability tables
-- Scope: parse task outbox + consume idempotency

CREATE TABLE IF NOT EXISTS tb_parse_task_outbox (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    message_key VARCHAR(128) NOT NULL COMMENT 'Unique message key for one logical delivery',
    task_id BIGINT UNSIGNED NOT NULL COMMENT 'Related parse task id',
    document_id BIGINT UNSIGNED NOT NULL COMMENT 'Related document id',
    file_record_id BIGINT UNSIGNED NOT NULL COMMENT 'Related file record id',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Outbox status: PENDING, FAILED, SENT',
    retry_count INT NOT NULL DEFAULT 0 COMMENT 'Dispatch retry count',
    next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Next dispatch retry time',
    last_error VARCHAR(512) DEFAULT NULL COMMENT 'Last dispatch error',
    sent_time DATETIME DEFAULT NULL COMMENT 'Final sent time',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_parse_task_outbox_message_key (message_key),
    KEY idx_parse_task_outbox_dispatch (status, next_retry_time),
    KEY idx_parse_task_outbox_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Parse task local outbox table';

CREATE TABLE IF NOT EXISTS tb_parse_task_consume_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    message_key VARCHAR(128) NOT NULL COMMENT 'Unique message key for idempotent consume claim',
    task_id BIGINT UNSIGNED NOT NULL COMMENT 'Related parse task id',
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT 'Consume status: PROCESSING, FAILED, SUCCESS',
    consume_count INT NOT NULL DEFAULT 1 COMMENT 'How many times this message was claimed',
    last_error VARCHAR(512) DEFAULT NULL COMMENT 'Last consume error',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_parse_task_consume_message_key (message_key),
    KEY idx_parse_task_consume_task_id (task_id),
    KEY idx_parse_task_consume_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Parse task consume idempotency record';
