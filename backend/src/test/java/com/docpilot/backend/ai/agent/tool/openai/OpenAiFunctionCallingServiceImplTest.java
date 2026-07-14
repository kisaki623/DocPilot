package com.docpilot.backend.ai.agent.tool.openai;

import com.docpilot.backend.ai.agent.dto.ToolCallRequest;
import com.docpilot.backend.ai.agent.service.ToolCallService;
import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.ai.agent.tool.spec.DefaultToolSpecProvider;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallStatus;
import com.docpilot.backend.ai.agent.tool.spec.ToolSpecRegistry;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiFunctionCallingServiceImplTest {

    private final ToolSpecRegistry registry = mock(ToolSpecRegistry.class);
    private final ToolCallService toolCallService = mock(ToolCallService.class);
    private final OpenAiFunctionCallingServiceImpl service = new OpenAiFunctionCallingServiceImpl(
            registry,
            toolCallService,
            new OpenAiToolSchemaAdapter(),
            new OpenAiToolCallParser(),
            new OpenAiToolResultAdapter()
    );

    @Test
    void shouldParseMockToolCallAndInvokeToolCallService() {
        stubVisibleSpecs();
        when(toolCallService.call(eq(7L), any())).thenReturn(ToolCallResult.success(
                DocumentRagQaTool.TOOL_NAME,
                Map.of("answer", "cache answer"),
                "hitCount=1"
        ));

        OpenAiFunctionCallingResult result = service.callTools(7L, "cache?", responseWithToolCalls("""
                {"id":"call-rag","type":"function","function":{"name":"rag_qa_tool","arguments":"{\\"documentId\\":101,\\"question\\":\\"cache?\\"}"}}
                """));

        assertThat(result.success()).isTrue();
        assertThat(result.tools()).isNotEmpty();
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolMessages()).hasSize(1);
        verify(toolCallService).call(eq(7L), org.mockito.ArgumentMatchers.argThat(request ->
                DocumentRagQaTool.TOOL_NAME.equals(request.getToolName())
                        && Long.valueOf(101L).equals(((Number) request.getArguments().get("documentId")).longValue())
                        && "cache?".equals(request.getArguments().get("question"))
        ));
    }

    @Test
    void shouldExecuteMultipleToolCallsInOrder() {
        stubVisibleSpecs();
        when(toolCallService.call(eq(7L), any())).thenAnswer(invocation -> {
            ToolCallRequest request = invocation.getArgument(1);
            return ToolCallResult.success(request.getToolName(), Map.of("ok", true), request.getToolName());
        });

        OpenAiFunctionCallingResult result = service.callTools(7L, "status then rag", responseWithToolCalls("""
                {"id":"call-status","type":"function","function":{"name":"document_status_tool","arguments":"{\\"documentId\\":101}"}},
                {"id":"call-rag","type":"function","function":{"name":"rag_qa_tool","arguments":"{\\"documentId\\":101,\\"question\\":\\"cache?\\"}"}}
                """));

        assertThat(result.success()).isTrue();
        assertThat(result.toolCalls()).extracting(OpenAiParsedToolCall::id)
                .containsExactly("call-status", "call-rag");
        assertThat(result.toolMessages()).extracting(OpenAiToolMessage::toolCallId)
                .containsExactly("call-status", "call-rag");
    }

    @Test
    void shouldReturnFailedResultForUnknownToolOrInvalidArgsWithoutThrowing() {
        stubVisibleSpecs();
        when(toolCallService.call(eq(7L), any())).thenThrow(new BusinessException(ErrorCode.BAD_REQUEST, "unknown tool"));

        OpenAiFunctionCallingResult result = service.callTools(7L, "unknown", responseWithToolCalls("""
                {"id":"call-unknown","type":"function","function":{"name":"unknown_tool","arguments":"{}"}}
                """));

        assertThat(result.success()).isFalse();
        assertThat(result.toolResults()).hasSize(1);
        assertThat(result.toolResults().get(0).status()).isEqualTo(ToolCallStatus.FAILED);
        assertThat(result.toolResults().get(0).errorType()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void shouldReturnSafeFailureForInvalidModelResponse() {
        stubVisibleSpecs();

        OpenAiFunctionCallingResult result = service.callTools(7L, "bad", "not-json");

        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo("BAD_REQUEST");
        assertThat(result.toolResults()).isEmpty();
        assertThat(result.toolMessages()).isEmpty();
    }

    private void stubVisibleSpecs() {
        when(registry.listLlmSelectable()).thenReturn(new DefaultToolSpecProvider().getToolSpecs().stream()
                .filter(spec -> !"document_rag_tool".equals(spec.name()))
                .toList());
    }

    private String responseWithToolCalls(String toolCallsJson) {
        return """
                {"choices":[{"message":{"tool_calls":[
                %s
                ]}}]}
                """.formatted(toolCallsJson);
    }
}
