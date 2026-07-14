package com.docpilot.backend.ai.agent.controller;

import com.docpilot.backend.ai.agent.dto.ToolCallRequest;
import com.docpilot.backend.ai.agent.service.ToolCallService;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
import com.docpilot.backend.ai.agent.tool.spec.ToolParameterSchema;
import com.docpilot.backend.ai.agent.tool.spec.ToolResultSchema;
import com.docpilot.backend.ai.agent.tool.spec.ToolRiskLevel;
import com.docpilot.backend.ai.agent.vo.ToolSpecResponse;
import com.docpilot.backend.common.context.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolControllerTest {

    private final ToolCallService toolCallService = mock(ToolCallService.class);
    private final AgentToolController controller = new AgentToolController(toolCallService);

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void shouldListTools() {
        ToolSpecResponse response = new ToolSpecResponse();
        response.setName("rag_qa_tool");
        response.setDescription("RAG QA");
        response.setParameterSchema(ToolParameterSchema.object(Map.of("question", "String")));
        response.setRequiredFields(Set.of("question"));
        response.setResultSchema(ToolResultSchema.object(Map.of("answer", "String")));
        response.setRiskLevel(ToolRiskLevel.MEDIUM);
        response.setSafeForLlmSelection(true);
        response.setCallableByToolCallApi(true);
        when(toolCallService.listTools()).thenReturn(List.of(response));

        var result = controller.listTools();

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).getName()).isEqualTo("rag_qa_tool");
    }

    @Test
    void shouldCallServiceWithCurrentUser() {
        UserHolder.setUserId(7L);
        ToolCallRequest request = new ToolCallRequest();
        request.setToolName("document_status_tool");
        request.setArguments(Map.of("documentId", 101L));
        ToolCallResult toolResult = ToolCallResult.success("document_status_tool", "ok", "done");
        when(toolCallService.call(7L, request)).thenReturn(toolResult);

        var result = controller.call(request);

        assertThat(result.data()).isSameAs(toolResult);
        verify(toolCallService).call(7L, request);
    }
}
