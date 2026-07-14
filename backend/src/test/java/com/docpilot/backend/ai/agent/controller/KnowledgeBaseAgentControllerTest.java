package com.docpilot.backend.ai.agent.controller;

import com.docpilot.backend.ai.agent.dto.KnowledgeBaseAgentRequest;
import com.docpilot.backend.ai.agent.service.KnowledgeBaseAgentService;
import com.docpilot.backend.ai.agent.vo.KnowledgeBaseAgentResponse;
import com.docpilot.backend.common.context.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseAgentControllerTest {

    private final KnowledgeBaseAgentService service = mock(KnowledgeBaseAgentService.class);
    private final KnowledgeBaseAgentController controller = new KnowledgeBaseAgentController(service);

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void shouldRunWithCurrentUserAndPathKnowledgeBaseId() {
        UserHolder.setUserId(7L);
        KnowledgeBaseAgentRequest request = new KnowledgeBaseAgentRequest();
        request.setTask("retrieve evidence chunks");
        KnowledgeBaseAgentResponse response = new KnowledgeBaseAgentResponse();
        response.setKnowledgeBaseId(99L);
        response.setDecision("search_tool");
        when(service.run(7L, 99L, request)).thenReturn(response);

        var result = controller.run(99L, request);

        assertThat(result.data()).isSameAs(response);
        verify(service).run(7L, 99L, request);
    }
}
