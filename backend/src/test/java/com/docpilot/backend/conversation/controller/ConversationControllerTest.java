package com.docpilot.backend.conversation.controller;

import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.conversation.dto.ConversationCreateRequest;
import com.docpilot.backend.conversation.dto.ConversationKnowledgeBaseBindRequest;
import com.docpilot.backend.conversation.service.ConversationService;
import com.docpilot.backend.conversation.vo.ConversationResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationControllerTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final ConversationController controller = new ConversationController(conversationService);

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void shouldCreateWithCurrentUser() {
        UserHolder.setUserId(7L);
        ConversationCreateRequest request = new ConversationCreateRequest();
        request.setTitle("DocPilot");
        request.setContextMode("AGENT_MEMORY");
        request.setBoundKnowledgeBaseId(3L);
        when(conversationService.create(org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any(), org.mockito.Mockito.any())).thenReturn(response());

        controller.create(request);

        verify(conversationService).create(7L, "DocPilot", "AGENT_MEMORY", 3L);
    }

    @Test
    void shouldListWithCurrentUser() {
        UserHolder.setUserId(7L);
        when(conversationService.list(7L, 20)).thenReturn(List.of());

        controller.list(20);

        verify(conversationService).list(7L, 20);
    }

    @Test
    void shouldBindKnowledgeBaseWithCurrentUser() {
        UserHolder.setUserId(7L);
        ConversationKnowledgeBaseBindRequest request = new ConversationKnowledgeBaseBindRequest();
        request.setKnowledgeBaseId(3L);
        when(conversationService.bindKnowledgeBase(7L, 10L, 3L)).thenReturn(response());

        controller.bindKnowledgeBase(10L, request);

        verify(conversationService).bindKnowledgeBase(7L, 10L, 3L);
    }

    private ConversationResponse response() {
        return new ConversationResponse(10L, "title", "RECENT_TURNS", "ACTIVE",
                null, false, false, null, null, null);
    }
}
