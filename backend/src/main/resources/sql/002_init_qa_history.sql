-- DocPilot P1 qa history table initialization
-- Scope: tb_qa_history

CREATE TABLE IF NOT EXISTS tb_qa_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    document_id BIGINT UNSIGNED NOT NULL COMMENT 'Related document id',
    question TEXT NOT NULL COMMENT 'Question content',
    answer LONGTEXT NOT NULL COMMENT 'Answer content',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    KEY idx_qa_history_user_document_time (user_id, document_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Document QA history table';

