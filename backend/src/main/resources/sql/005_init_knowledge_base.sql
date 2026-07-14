CREATE TABLE IF NOT EXISTS tb_knowledge_base (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    name VARCHAR(128) NOT NULL COMMENT 'Knowledge base name',
    description VARCHAR(512) DEFAULT NULL COMMENT 'Knowledge base description',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE, REMOVED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_base_user_name (user_id, name),
    KEY idx_knowledge_base_user_id (user_id),
    KEY idx_knowledge_base_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='RAG knowledge base table';

CREATE TABLE IF NOT EXISTS tb_knowledge_base_document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    knowledge_base_id BIGINT UNSIGNED NOT NULL COMMENT 'Knowledge base id',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    document_id BIGINT UNSIGNED NOT NULL COMMENT 'Document id',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE, REMOVED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_kb_document (knowledge_base_id, document_id),
    KEY idx_kb_document_user_kb (user_id, knowledge_base_id),
    KEY idx_kb_document_document_id (document_id),
    KEY idx_kb_document_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='RAG knowledge base document relation table';
