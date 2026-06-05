package com.docpilot.backend.knowledge;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseSchemaTest {

    @Test
    void shouldContainKnowledgeBaseTablesAndSoftDeleteStatus() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/sql/005_init_knowledge_base.sql"),
                StandardCharsets.UTF_8
        );

        assertThat(sql).contains("tb_knowledge_base");
        assertThat(sql).contains("tb_knowledge_base_document");
        assertThat(sql).contains("status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'");
        assertThat(sql).contains("uk_kb_document");
        assertThat(sql).contains("idx_kb_document_status");
    }
}
