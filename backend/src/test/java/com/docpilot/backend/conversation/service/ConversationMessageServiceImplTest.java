package com.docpilot.backend.conversation.service;

import com.docpilot.backend.ai.context.ContextAssemblyRequest;
import com.docpilot.backend.ai.context.ContextAssemblyResult;
import com.docpilot.backend.ai.context.ContextTrace;
import com.docpilot.backend.ai.context.token.TokenEstimator;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;
import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.conversation.constant.ConversationMessageRole;
import com.docpilot.backend.conversation.entity.Conversation;
import com.docpilot.backend.conversation.entity.ConversationMessage;
import com.docpilot.backend.conversation.mapper.ConversationMapper;
import com.docpilot.backend.conversation.mapper.ConversationMessageMapper;
import com.docpilot.backend.conversation.service.ConversationContextTraceService;
import com.docpilot.backend.conversation.service.impl.ConversationMessageServiceImpl;
import com.docpilot.backend.conversation.vo.ConversationMessageResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationMessageServiceImplTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final ConversationMapper conversationMapper = mock(ConversationMapper.class);
    private final ConversationMessageMapper conversationMessageMapper = mock(ConversationMessageMapper.class);
    private final ConversationContextTraceService contextTraceService = mock(ConversationContextTraceService.class);
    private final com.docpilot.backend.ai.context.ContextAssemblyService contextAssemblyService =
            mock(com.docpilot.backend.ai.context.ContextAssemblyService.class);
    private final AiAnswerService aiAnswerService = mock(AiAnswerService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final ConversationMessageServiceImpl service = new ConversationMessageServiceImpl(
            conversationService,
            conversationMapper,
            conversationMessageMapper,
            contextTraceService,
            contextAssemblyService,
            aiAnswerService,
            new TokenEstimator(),
            transactionManager
    );

    @Test
    void shouldSkipModelWhenRequiredRagHasNoEvidence() {
        givenTransaction();
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation());
        when(conversationMapper.selectActiveForUpdate(7L, 10L)).thenReturn(conversation());
        when(contextAssemblyService.buildContext(any())).thenReturn(contextResult(true));
        when(conversationMessageMapper.selectMaxSequenceNo(7L, 10L)).thenReturn(0, 1);
        doAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(100L + message.getSequenceNo());
            return 1;
        }).when(conversationMessageMapper).insert(any(ConversationMessage.class));

        ConversationMessageResponse response = service.send(7L, 10L, "根据知识库回答");

        assertThat(response.content()).contains("没有找到足够证据");
        assertThat(response.contextTrace().messageId()).isEqualTo(102L);
        assertThat(response.contextTrace().modelCallSkipped()).isTrue();
        verify(contextTraceService).save(7L, response.contextTrace());
        verify(aiAnswerService, never()).answer(any(), any());
    }

    @Test
    void shouldCallModelAndSaveUserThenAssistantMessages() {
        givenTransaction();
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation());
        when(conversationMapper.selectActiveForUpdate(7L, 10L)).thenReturn(conversation());
        when(contextAssemblyService.buildContext(any())).thenReturn(contextResult(false));
        when(aiAnswerService.answer("context", "hello")).thenReturn("answer");
        when(conversationMessageMapper.selectMaxSequenceNo(7L, 10L)).thenReturn(0, 1);
        doAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(100L + message.getSequenceNo());
            return 1;
        }).when(conversationMessageMapper).insert(any(ConversationMessage.class));

        ConversationMessageResponse response = service.send(7L, 10L, "hello");

        ArgumentCaptor<ConversationMessage> messageCaptor = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(conversationMessageMapper, org.mockito.Mockito.times(2)).insert(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues()).extracting(ConversationMessage::getRole)
                .containsExactly(ConversationMessageRole.USER, ConversationMessageRole.ASSISTANT);
        assertThat(response.content()).isEqualTo("answer");
        verify(contextTraceService).save(7L, response.contextTrace());
        verify(aiAnswerService).answer("context", "hello");
        verify(conversationMessageMapper, org.mockito.Mockito.times(1)).selectMaxSequenceNo(7L, 10L);
    }

    @Test
    void shouldNotFailAnswerWhenTracePersistenceFails() {
        givenTransaction();
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation());
        when(conversationMapper.selectActiveForUpdate(7L, 10L)).thenReturn(conversation());
        when(contextAssemblyService.buildContext(any())).thenReturn(contextResult(false));
        when(aiAnswerService.answer("context", "hello")).thenReturn("answer");
        when(conversationMessageMapper.selectMaxSequenceNo(7L, 10L)).thenReturn(0, 1);
        doAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(100L + message.getSequenceNo());
            return 1;
        }).when(conversationMessageMapper).insert(any(ConversationMessage.class));
        doThrow(new IllegalStateException("trace down")).when(contextTraceService).save(any(), any());

        ConversationMessageResponse response = service.send(7L, 10L, "hello");

        assertThat(response.content()).isEqualTo("answer");
        assertThat(response.contextTrace().messageId()).isEqualTo(102L);
    }

    @Test
    void shouldNotPersistMessagesWhenModelCallFails() {
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation());
        when(contextAssemblyService.buildContext(any())).thenReturn(contextResult(false));
        when(aiAnswerService.answer("context", "hello")).thenThrow(new IllegalStateException("model down"));

        assertThatThrownBy(() -> service.send(7L, 10L, "hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model down");

        verify(conversationMessageMapper, never()).insert(any(ConversationMessage.class));
        verify(conversationMapper, never()).selectActiveForUpdate(any(), any());
        verify(conversationMessageMapper, never()).selectMaxSequenceNo(any(), any());
    }

    @Test
    void shouldReadPersistedTraceByMessage() {
        ContextTrace trace = contextResult(false).trace().withMessageId(102L);
        when(contextTraceService.getByMessage(7L, 10L, 102L)).thenReturn(trace);

        ContextTrace response = service.getTrace(7L, 10L, 102L);

        assertThat(response).isSameAs(trace);
    }

    private Conversation conversation() {
        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setUserId(7L);
        conversation.setContextMode("RECENT_TURNS");
        return conversation;
    }

    private void givenTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private ContextAssemblyResult contextResult(boolean modelCallSkipped) {
        ContextTrace trace = new ContextTrace(
                10L,
                null,
                modelCallSkipped ? "AGENT_MEMORY" : "RECENT_TURNS",
                false,
                0,
                0,
                false,
                0,
                List.of(),
                modelCallSkipped,
                modelCallSkipped,
                3L,
                0,
                modelCallSkipped,
                Map.of(),
                12000,
                10,
                false,
                List.of(),
                modelCallSkipped,
                modelCallSkipped ? "NO_EVIDENCE" : "",
                modelCallSkipped
        );
        return new ContextAssemblyResult(
                "context",
                List.of(),
                List.of(),
                trace,
                modelCallSkipped,
                modelCallSkipped,
                modelCallSkipped,
                modelCallSkipped ? "当前知识库中没有找到足够证据，无法基于知识库回答该问题。" : "",
                List.<KnowledgeBaseRagEvidenceCitation>of()
        );
    }
}
