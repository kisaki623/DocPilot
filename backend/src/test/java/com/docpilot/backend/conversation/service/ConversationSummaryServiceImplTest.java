package com.docpilot.backend.conversation.service;

import com.docpilot.backend.ai.context.token.TokenEstimator;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.conversation.constant.ConversationMessageRole;
import com.docpilot.backend.conversation.constant.ConversationSummaryStatus;
import com.docpilot.backend.conversation.entity.ConversationMessage;
import com.docpilot.backend.conversation.entity.ConversationSummary;
import com.docpilot.backend.conversation.mapper.ConversationMessageMapper;
import com.docpilot.backend.conversation.mapper.ConversationSummaryMapper;
import com.docpilot.backend.conversation.service.impl.ConversationSummaryServiceImpl;
import com.docpilot.backend.conversation.vo.ConversationSummaryResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationSummaryServiceImplTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final ConversationSummaryMapper summaryMapper = mock(ConversationSummaryMapper.class);
    private final ConversationMessageMapper messageMapper = mock(ConversationMessageMapper.class);
    private final ConversationSummaryServiceImpl service = new ConversationSummaryServiceImpl(
            conversationService,
            summaryMapper,
            messageMapper,
            new TokenEstimator()
    );

    @Test
    void shouldRefreshSummaryFromRecentMessages() {
        ConversationSummary active = new ConversationSummary();
        active.setSummaryVersion(2);
        when(summaryMapper.selectActiveSummary(7L, 10L)).thenReturn(active);
        when(messageMapper.selectActiveByConversation(7L, 10L, 40)).thenReturn(List.of(
                message(1, ConversationMessageRole.USER, "hello"),
                message(2, ConversationMessageRole.ASSISTANT, "answer")
        ));
        when(summaryMapper.insert(any(ConversationSummary.class))).thenReturn(1);

        ConversationSummaryResponse response = service.refreshSummary(7L, 10L);

        verify(conversationService).requireOwnedActive(7L, 10L);
        verify(summaryMapper).softDeleteByConversation(7L, 10L);
        ArgumentCaptor<ConversationSummary> captor = ArgumentCaptor.forClass(ConversationSummary.class);
        verify(summaryMapper).insert(captor.capture());
        ConversationSummary saved = captor.getValue();
        assertThat(saved.getSummaryVersion()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo(ConversationSummaryStatus.ACTIVE);
        assertThat(saved.getCoveredStartSeq()).isEqualTo(1);
        assertThat(saved.getCoveredEndSeq()).isEqualTo(2);
        assertThat(saved.getSummary()).contains("USER #1: hello", "ASSISTANT #2: answer");
        assertThat(response.summaryVersion()).isEqualTo(3);
    }

    @Test
    void shouldRejectRefreshWhenConversationHasNoMessages() {
        when(messageMapper.selectActiveByConversation(7L, 10L, 40)).thenReturn(List.of());

        assertThatThrownBy(() -> service.refreshSummary(7L, 10L))
                .isInstanceOf(BusinessException.class);
    }

    private ConversationMessage message(int sequenceNo, String role, String content) {
        ConversationMessage message = new ConversationMessage();
        message.setConversationId(10L);
        message.setUserId(7L);
        message.setRole(role);
        message.setContent(content);
        message.setSequenceNo(sequenceNo);
        return message;
    }
}
