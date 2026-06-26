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
        assertThat(sql).contains("bound_knowledge_base_id BIGINT UNSIGNED DEFAULT NULL");
        assertThat(sql).contains("UNIQUE KEY uk_conversation_sequence (conversation_id, sequence_no)");
        assertThat(sql).contains("UNIQUE KEY uk_context_trace_message (message_id)");
        assertThat(sql).contains("Status: SUGGESTED, ACTIVE, IGNORED, ARCHIVED, DELETED");
        assertThat(sql).contains("KEY idx_memory_user_status (user_id, status)");
    }

    private String readSqlScript() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/sql/007_init_conversation_context.sql")) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
