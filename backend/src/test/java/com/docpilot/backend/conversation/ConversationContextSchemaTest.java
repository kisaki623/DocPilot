package com.docpilot.backend.conversation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationContextSchemaTest {

    @Test
    void shouldContainConversationContextTables() throws IOException {
        String sql = readSqlScript();

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS tb_conversation");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS tb_conversation_message");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS tb_conversation_summary");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS tb_context_trace");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS tb_user_memory");
        assertThat(sql).contains("context_mode VARCHAR(32) NOT NULL DEFAULT 'RECENT_TURNS'");
        assertThat(sql).contains("grounding_policy VARCHAR(32) DEFAULT NULL");
        assertThat(sql).contains("route_decision VARCHAR(64) DEFAULT NULL");
        assertThat(sql).contains("llm_called TINYINT(1) DEFAULT NULL");
        assertThat(sql).contains("bound_knowledge_base_id BIGINT UNSIGNED DEFAULT NULL");
        assertThat(sql).contains("UNIQUE KEY uk_conversation_sequence (conversation_id, sequence_no)");
        assertThat(sql).contains("UNIQUE KEY uk_context_trace_message (message_id)");
        assertThat(sql).contains("Status: SUGGESTED, ACTIVE, IGNORED, ARCHIVED, DELETED");
        assertThat(sql).contains("KEY idx_memory_user_status (user_id, status)");
    }

    @Test
    void shouldContainIncrementalGroundingTraceMigration() throws IOException {
        String sql = readSqlScript("/sql/008_add_context_trace_grounding.sql");

        assertThat(sql).contains("COLUMN_NAME = 'grounding_policy'");
        assertThat(sql).contains("COLUMN_NAME = 'route_decision'");
        assertThat(sql).contains("COLUMN_NAME = 'llm_called'");
        assertThat(sql).contains("ALTER TABLE tb_context_trace ADD COLUMN grounding_policy");
        assertThat(sql).contains("ALTER TABLE tb_context_trace ADD COLUMN route_decision");
        assertThat(sql).contains("ALTER TABLE tb_context_trace ADD COLUMN llm_called");
    }

    private String readSqlScript() throws IOException {
        return readSqlScript("/sql/007_init_conversation_context.sql");
    }

    private String readSqlScript(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
