package com.docpilot.backend.conversation.controller;

import com.docpilot.backend.ai.context.ContextTrace;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.conversation.dto.ConversationMessageSendRequest;
import com.docpilot.backend.conversation.service.ConversationMessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConversationMessageControllerTest {

    private final ConversationMessageService conversationMessageService = mock(ConversationMessageService.class);
    private final ConversationMessageController controller = new ConversationMessageController(conversationMessageService);

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void shouldSendWithCurrentUser() {
        UserHolder.setUserId(7L);
        ConversationMessageSendRequest request = new ConversationMessageSendRequest();
        request.setContent("hello");

        controller.send(10L, request);

        verify(conversationMessageService).send(7L, 10L, "hello");
    }

    @Test
    void shouldGetTraceWithCurrentUser() {
        UserHolder.setUserId(7L);

        controller.trace(10L, 102L);

        verify(conversationMessageService).getTrace(7L, 10L, 102L);
    }

    private ContextTrace trace() {
        return new ContextTrace(10L, 102L, "RECENT_TURNS", false, 0, 0,
                false, 0, List.of(), false, false, null, 0, false, Map.of(),
                8000, 20, false, List.of(), false, "", false);
    }
}
