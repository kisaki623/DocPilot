package com.docpilot.backend.bootstrap;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoMysqlBootstrapSchemaTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "tb_user", "tb_file_record", "tb_document", "tb_parse_task", "tb_qa_history",
            "tb_parse_task_outbox", "tb_parse_task_consume_record", "tb_agent_task", "tb_agent_step",
            "tb_document_chunk", "tb_knowledge_base", "tb_knowledge_base_document", "tb_conversation",
            "tb_conversation_message", "tb_conversation_summary", "tb_context_trace", "tb_user_memory"
    );

    @Test
    void shouldBootstrapEveryPersistentTableRequiredByDemo() throws IOException {
        String sql = readDemoBootstrap();

        assertThat(EXPECTED_TABLES).allSatisfy(table ->
                assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table));
        assertThat(sql).contains("status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Document status: ACTIVE, REMOVED'");
        assertThat(sql).contains("KEY idx_document_status (status)");
        assertThat(sql).contains("question TEXT NOT NULL COMMENT 'Question content'");
        assertThat(sql).contains("answer LONGTEXT NOT NULL COMMENT 'Answer content'");
        assertThat(sql).contains("KEY idx_qa_history_user_document_time (user_id, document_id, create_time)");
    }

    @Test
    void shouldRetainCriticalRagConversationAndAgentConstraints() throws IOException {
        String sql = readDemoBootstrap();

        assertThat(sql).contains("UNIQUE KEY uk_document_version_chunk (document_id, index_version, chunk_index)");
        assertThat(sql).contains("UNIQUE KEY uk_kb_document (knowledge_base_id, document_id)");
        assertThat(sql).contains("UNIQUE KEY uk_conversation_sequence (conversation_id, sequence_no)");
        assertThat(sql).contains("UNIQUE KEY uk_context_trace_message (message_id)");
        assertThat(sql).contains("KEY idx_memory_user_status (user_id, status)");
        assertThat(sql).contains("KEY idx_task_step (task_id, step_index)");
    }

    private String readDemoBootstrap() throws IOException {
        Path initDirectory = Path.of("..", "deploy", "mysql", "init");
        return String.join("\n", Files.readString(initDirectory.resolve("00_init_docpilot.sql"), StandardCharsets.UTF_8),
                Files.readString(initDirectory.resolve("01_add_agent_tables.sql"), StandardCharsets.UTF_8),
                Files.readString(initDirectory.resolve("02_init_rag_conversation_tables.sql"), StandardCharsets.UTF_8));
    }
}
