package com.docpilot.backend.ai.agent.tool.openai;

import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallStatus;
import com.docpilot.backend.ai.rag.RagRetrievalHit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiToolResultAdapterTest {

    private final OpenAiToolResultAdapter adapter = new OpenAiToolResultAdapter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldConvertSuccessResultToToolMessage() throws Exception {
        RagRetrievalHit hit = new RagRetrievalHit(
                1, "vec-1", 0.9d, 7L, 101L, 1, 301L, 0, "snippet", "hash", 0, 7, 2, "mock"
        );
        ToolCallResult result = ToolCallResult.success(
                "rag_qa_tool",
                Map.of("answer", "answer [1]"),
                "hitCount=1",
                12L,
                List.of(hit.toCitation()),
                List.of(hit)
        );

        OpenAiToolMessage message = adapter.toToolMessage(new OpenAiParsedToolCall("call-1", "rag_qa_tool", Map.of()), result);

        assertThat(message.role()).isEqualTo("tool");
        assertThat(message.toolCallId()).isEqualTo("call-1");
        var json = objectMapper.readTree(message.content());
        assertThat(json.path("toolName").asText()).isEqualTo("rag_qa_tool");
        assertThat(json.path("status").asText()).isEqualTo(ToolCallStatus.SUCCESS.name());
        assertThat(json.path("citations")).hasSize(1);
        assertThat(json.path("retrievalHits")).hasSize(1);
    }

    @Test
    void shouldSanitizeFailedResultMessage() {
        ToolCallResult result = ToolCallResult.failed(
                "rag_qa_tool",
                "IllegalStateException",
                "secret=sk-test-secret jdbc:mysql://internal-host/docpilot"
        );

        OpenAiToolMessage message = adapter.toToolMessage(new OpenAiParsedToolCall("call-1", "rag_qa_tool", Map.of()), result);

        assertThat(message.content()).contains("IllegalStateException");
        assertThat(message.content()).doesNotContain("sk-test-secret");
        assertThat(message.content()).doesNotContain("jdbc:mysql://internal-host");
        assertThat(message.content()).doesNotContain("java.lang.IllegalStateException");
    }
}
