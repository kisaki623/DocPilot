package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkSchemaTest {

    @Test
    void shouldKeepDocumentChunkSchemaScriptAsReferenceMigration() throws IOException {
        String sql = readSqlScript();

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS tb_document_chunk");
        assertThat(sql).contains("document_id BIGINT UNSIGNED NOT NULL");
        assertThat(sql).contains("user_id BIGINT UNSIGNED NOT NULL");
        assertThat(sql).contains("chunk_index INT NOT NULL");
        assertThat(sql).contains("content_hash VARCHAR(64) NOT NULL");
        assertThat(sql).contains("index_status VARCHAR(32) NOT NULL");
        assertThat(sql).contains("index_version INT NOT NULL DEFAULT 1");
        assertThat(sql).contains("KEY idx_document_chunk_document_id (document_id)");
        assertThat(sql).contains("KEY idx_document_chunk_user_document (user_id, document_id)");
        assertThat(sql).contains("UNIQUE KEY uk_document_version_chunk (document_id, index_version, chunk_index)");
        assertThat(sql).contains("KEY idx_document_chunk_status (index_status)");
    }

    private String readSqlScript() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/sql/004_init_document_chunk.sql")) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
