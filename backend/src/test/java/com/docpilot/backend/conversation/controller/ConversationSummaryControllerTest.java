package com.docpilot.backend.conversation.controller;

import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.conversation.service.ConversationSummaryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConversationSummaryControllerTest {

    private final ConversationSummaryService summaryService = mock(ConversationSummaryService.class);
    private final ConversationSummaryController controller = new ConversationSummaryController(summaryService);

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void shouldRefreshWithCurrentUser() {
        UserHolder.setUserId(7L);

        controller.refreshSummary(10L);

        verify(summaryService).refreshSummary(7L, 10L);
    }
}
