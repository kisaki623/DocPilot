package com.docpilot.backend.ai.agent.tool.openai;

import com.docpilot.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiToolCallParserTest {

    private final OpenAiToolCallParser parser = new OpenAiToolCallParser();

    @Test
    void shouldParseSingleToolCallArgumentsJsonString() {
        var calls = parser.parse("""
                {"choices":[{"message":{"tool_calls":[{"id":"call-1","type":"function","function":{"name":"rag_qa_tool","arguments":"{\\"documentId\\":101,\\"question\\":\\"cache?\\",\\"topK\\":4}"}}]}}]}
                """);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).id()).isEqualTo("call-1");
        assertThat(calls.get(0).toolName()).isEqualTo("rag_qa_tool");
        assertThat(calls.get(0).arguments()).containsEntry("documentId", 101);
        assertThat(calls.get(0).arguments()).containsEntry("question", "cache?");
    }

    @Test
    void shouldParseMultipleToolCallsInOrder() {
        var calls = parser.parse("""
                {"choices":[{"message":{"tool_calls":[
                  {"id":"call-status","type":"function","function":{"name":"document_status_tool","arguments":"{\\"documentId\\":101}"}},
                  {"id":"call-rag","type":"function","function":{"name":"rag_qa_tool","arguments":"{\\"documentId\\":101,\\"question\\":\\"cache?\\"}"}}
                ]}}]}
                """);

        assertThat(calls).extracting(OpenAiParsedToolCall::id)
                .containsExactly("call-status", "call-rag");
    }

    @Test
    void shouldRejectInvalidProviderJsonAndInvalidArgumentsJson() {
        assertThrows(BusinessException.class, () -> parser.parse("not-json"));
        assertThrows(BusinessException.class, () -> parser.parse("""
                {"choices":[{"message":{"tool_calls":[{"id":"call-1","type":"function","function":{"name":"rag_qa_tool","arguments":"not-json"}}]}}]}
                """));
    }

    @Test
    void shouldRejectMissingNameAndNonFunctionType() {
        assertThrows(BusinessException.class, () -> parser.parse("""
                {"choices":[{"message":{"tool_calls":[{"id":"call-1","type":"function","function":{"arguments":"{}"}}]}}]}
                """));
        assertThrows(BusinessException.class, () -> parser.parse("""
                {"choices":[{"message":{"tool_calls":[{"id":"call-1","type":"custom","function":{"name":"rag_qa_tool","arguments":"{}"}}]}}]}
                """));
    }
}
