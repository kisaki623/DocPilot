package com.docpilot.backend.conversation.service;

import com.docpilot.backend.ai.context.ContextAssemblyRequest;
import com.docpilot.backend.ai.context.ContextAssemblyResult;
import com.docpilot.backend.ai.context.ContextTrace;
import com.docpilot.backend.ai.context.GroundingPolicy;
import com.docpilot.backend.ai.context.PromptMessage;
import com.docpilot.backend.ai.context.RouteDecision;
import com.docpilot.backend.ai.context.token.TokenEstimator;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;
import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.ai.service.ConversationAnswerRequest;
import com.docpilot.backend.common.exception.BusinessException;
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
import static org.mockito.Mockito.times;
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
        assertThat(response.contextTrace().technicalDetails().traceId()).isEqualTo("ctx-10-102");
        assertThat(response.contextTrace().technicalDetails().timingsMs()).containsEntry("modelCall", 0L);
        assertThat(response.contextTrace().modelCallSkipped()).isTrue();
        assertThat(response.contextTrace().llmCalled()).isFalse();
        verify(contextTraceService).save(7L, response.contextTrace());
        verify(aiAnswerService, never()).answer(any(), any());
        verify(aiAnswerService, never()).answerConversation(any());
    }

    @Test
    void shouldCallModelAndSaveUserThenAssistantMessages() {
        givenTransaction();
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation());
        when(conversationMapper.selectActiveForUpdate(7L, 10L)).thenReturn(conversation());
        when(contextAssemblyService.buildContext(any())).thenReturn(contextResult(false));
        when(aiAnswerService.answerConversation(any())).thenReturn("answer");
        when(conversationMessageMapper.selectMaxSequenceNo(7L, 10L)).thenReturn(0, 1);
        doAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(100L + message.getSequenceNo());
            return 1;
        }).when(conversationMessageMapper).insert(any(ConversationMessage.class));

        ConversationMessageResponse response = service.send(7L, 10L, "hello");

        ArgumentCaptor<ConversationMessage> messageCaptor = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(conversationMessageMapper, times(2)).insert(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues()).extracting(ConversationMessage::getRole)
                .containsExactly(ConversationMessageRole.USER, ConversationMessageRole.ASSISTANT);
        assertThat(response.content()).isEqualTo("answer");
        assertThat(response.contextTrace().llmCalled()).isTrue();
        assertThat(response.contextTrace().technicalDetails().traceId()).isEqualTo("ctx-10-102");
        assertThat(response.contextTrace().technicalDetails().timingsMs()).containsKey("modelCall");
        verify(contextTraceService).save(7L, response.contextTrace());
        ArgumentCaptor<ConversationAnswerRequest> answerCaptor = ArgumentCaptor.forClass(ConversationAnswerRequest.class);
        verify(aiAnswerService).answerConversation(answerCaptor.capture());
        assertThat(answerCaptor.getValue().question()).isEqualTo("hello");
        assertThat(answerCaptor.getValue().groundingPolicy()).isEqualTo(GroundingPolicy.MODEL_ONLY);
        assertThat(answerCaptor.getValue().routeDecision()).isEqualTo(RouteDecision.MODEL_ONLY);
        verify(aiAnswerService, never()).answer(any(), any());
        verify(conversationMessageMapper, times(1)).selectMaxSequenceNo(7L, 10L);
    }

    @Test
    void shouldPersistAndReturnContextCitationsWhenSendingRagAnswer() {
        givenTransaction();
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation());
        when(conversationMapper.selectActiveForUpdate(7L, 10L)).thenReturn(conversation());
        when(contextAssemblyService.buildContext(any())).thenReturn(contextResultWithCitation());
        when(aiAnswerService.answerConversation(any())).thenReturn("grounded answer");
        when(conversationMessageMapper.selectMaxSequenceNo(7L, 10L)).thenReturn(0, 1);
        doAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(100L + message.getSequenceNo());
            return 1;
        }).when(conversationMessageMapper).insert(any(ConversationMessage.class));

        ConversationMessageResponse response = service.send(7L, 10L, "P1 SLA?");

        assertThat(response.citations()).containsExactly(citation());
        assertThat(response.contextTrace().citations()).containsExactly(citation());
        verify(contextTraceService).save(7L, response.contextTrace());
    }


    @Test
    void shouldCallModelWhenAutoRagRetrievalHasNoEvidence() {
        givenTransaction();
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation());
        when(conversationMapper.selectActiveForUpdate(7L, 10L)).thenReturn(conversation());
        when(contextAssemblyService.buildContext(any())).thenReturn(autoNoEvidenceContextResult());
        when(aiAnswerService.answerConversation(any())).thenReturn("model fallback answer");
        when(conversationMessageMapper.selectMaxSequenceNo(7L, 10L)).thenReturn(0, 1);
        doAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(100L + message.getSequenceNo());
            return 1;
        }).when(conversationMessageMapper).insert(any(ConversationMessage.class));

        ConversationMessageResponse response = service.send(7L, 10L, "根据知识库回答");

        assertThat(response.content()).isEqualTo("model fallback answer");
        assertThat(response.contextTrace().routeDecision()).isEqualTo(RouteDecision.AUTO_NO_EVIDENCE_MODEL.name());
        assertThat(response.contextTrace().modelCallSkipped()).isFalse();
        assertThat(response.contextTrace().llmCalled()).isTrue();
        verify(aiAnswerService).answerConversation(any());
    }

    @Test
    void shouldSkipModelWhenAutoRagRequiredRetrievalHasNoEvidence() {
        givenTransaction();
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation());
        when(conversationMapper.selectActiveForUpdate(7L, 10L)).thenReturn(conversation());
        when(contextAssemblyService.buildContext(any())).thenReturn(autoRequiredNoEvidenceContextResult());
        when(conversationMessageMapper.selectMaxSequenceNo(7L, 10L)).thenReturn(0, 1);
        doAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(100L + message.getSequenceNo());
            return 1;
        }).when(conversationMessageMapper).insert(any(ConversationMessage.class));

        ConversationMessageResponse response = service.send(7L, 10L, "请引用文档回答");

        assertThat(response.content()).contains("没有找到足够证据");
        assertThat(response.contextTrace().routeDecision())
                .isEqualTo(RouteDecision.AUTO_REQUIRED_NO_EVIDENCE_FALLBACK.name());
        assertThat(response.contextTrace().modelCallSkipped()).isTrue();
        assertThat(response.contextTrace().llmCalled()).isFalse();
        verify(aiAnswerService, never()).answerConversation(any());
    }

    @Test
    void shouldRollbackMessagesWhenTracePersistenceFails() {
        givenTransaction();
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation());
        when(conversationMapper.selectActiveForUpdate(7L, 10L)).thenReturn(conversation());
        when(contextAssemblyService.buildContext(any())).thenReturn(contextResult(false));
        when(aiAnswerService.answerConversation(any())).thenReturn("answer");
        when(conversationMessageMapper.selectMaxSequenceNo(7L, 10L)).thenReturn(0, 1);
        doAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(100L + message.getSequenceNo());
            return 1;
        }).when(conversationMessageMapper).insert(any(ConversationMessage.class));
        doThrow(new IllegalStateException("trace down")).when(contextTraceService).save(any(), any());

        assertThatThrownBy(() -> service.send(7L, 10L, "hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trace down");

        verify(transactionManager).rollback(any());
    }

    @Test
    void shouldNotPersistMessagesWhenModelCallFails() {
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation());
        when(contextAssemblyService.buildContext(any())).thenReturn(contextResult(false));
        when(aiAnswerService.answerConversation(any())).thenThrow(new IllegalStateException("model down"));

        assertThatThrownBy(() -> service.send(7L, 10L, "hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model down");

        verify(conversationMessageMapper, never()).insert(any(ConversationMessage.class));
        verify(conversationMapper, never()).selectActiveForUpdate(any(), any());
        verify(conversationMessageMapper, never()).selectMaxSequenceNo(any(), any());
    }

    @Test
    void shouldPassRequestedGroundingPolicyToContextAssembly() {
        givenTransaction();
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation());
        when(conversationMapper.selectActiveForUpdate(7L, 10L)).thenReturn(conversation());
        when(contextAssemblyService.buildContext(any())).thenReturn(contextResult(false));
        when(aiAnswerService.answerConversation(any())).thenReturn("answer");
        when(conversationMessageMapper.selectMaxSequenceNo(7L, 10L)).thenReturn(0, 1);
        doAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(100L + message.getSequenceNo());
            return 1;
        }).when(conversationMessageMapper).insert(any(ConversationMessage.class));

        service.send(7L, 10L, "hello", "MODEL_ONLY");

        ArgumentCaptor<ContextAssemblyRequest> requestCaptor = ArgumentCaptor.forClass(ContextAssemblyRequest.class);
        verify(contextAssemblyService).buildContext(requestCaptor.capture());
        assertThat(requestCaptor.getValue().groundingPolicy()).isEqualTo("MODEL_ONLY");
    }

    @Test
    void shouldRejectInvalidGroundingPolicy() {
        assertThatThrownBy(() -> service.send(7L, 10L, "hello", "STRICT_GROUNDED"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("groundingPolicy is invalid");

        verify(contextAssemblyService, never()).buildContext(any());
        verify(aiAnswerService, never()).answerConversation(any());
    }

    @Test
    void shouldAttachPersistedTraceWhenListingMessages() {
        ConversationMessage userMessage = message(101L, ConversationMessageRole.USER, 1, "hello");
        ConversationMessage assistantMessage = message(102L, ConversationMessageRole.ASSISTANT, 2, "answer");
        ContextTrace trace = contextResult(false).trace()
                .withMessageId(102L)
                .withCitations(List.of(citation()));
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation());
        when(conversationMessageMapper.selectActiveByConversation(7L, 10L, 50))
                .thenReturn(List.of(assistantMessage, userMessage));
        when(contextTraceService.listByMessages(7L, 10L, List.of(102L))).thenReturn(Map.of(102L, trace));

        List<ConversationMessageResponse> responses = service.list(7L, 10L, null);

        assertThat(responses).extracting(ConversationMessageResponse::role)
                .containsExactly(ConversationMessageRole.USER, ConversationMessageRole.ASSISTANT);
        assertThat(responses.get(0).contextTrace()).isNull();
        assertThat(responses.get(1).contextTrace()).isSameAs(trace);
        assertThat(responses.get(1).citations()).containsExactly(citation());
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

    private ConversationMessage message(Long id, String role, int sequenceNo, String content) {
        ConversationMessage message = new ConversationMessage();
        message.setId(id);
        message.setUserId(7L);
        message.setConversationId(10L);
        message.setRole(role);
        message.setSequenceNo(sequenceNo);
        message.setContent(content);
        return message;
    }

    private KnowledgeBaseRagEvidenceCitation citation() {
        return new KnowledgeBaseRagEvidenceCitation(
                1,
                3L,
                83L,
                "SLA Guide",
                1,
                8301L,
                2,
                10,
                120,
                "hash-83",
                "P1 incidents respond within 10 minutes.",
                "P1 incidents respond within 10 minutes.",
                10,
                50,
                "SLA / P1",
                "paragraph",
                2,
                "page:2#block:5",
                "PAGE",
                0.91D,
                0.91D,
                null,
                null,
                null
        );
    }

    private void givenTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private ContextAssemblyResult contextResult(boolean modelCallSkipped) {
        ContextTrace trace = new ContextTrace(
                10L,
                null,
                modelCallSkipped ? "AGENT_MEMORY" : "RECENT_TURNS",
                modelCallSkipped ? GroundingPolicy.STRICT_KB.name() : GroundingPolicy.MODEL_ONLY.name(),
                modelCallSkipped ? RouteDecision.STRICT_NO_EVIDENCE_FALLBACK.name() : RouteDecision.MODEL_ONLY.name(),
                null,
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
                modelCallSkipped ? "STRICT_KB_NO_EVIDENCE" : "",
                modelCallSkipped
        );
        return new ContextAssemblyResult(
                "context",
                List.of(new PromptMessage("user", "hello")),
                List.of(),
                trace,
                modelCallSkipped,
                modelCallSkipped,
                modelCallSkipped,
                modelCallSkipped ? "当前知识库中没有找到足够证据，无法基于知识库回答该问题。" : "",
                List.<KnowledgeBaseRagEvidenceCitation>of()
        );
    }

    private ContextAssemblyResult autoNoEvidenceContextResult() {
        ContextTrace trace = new ContextTrace(
                10L,
                null,
                "AGENT_MEMORY",
                GroundingPolicy.AUTO_RAG.name(),
                RouteDecision.AUTO_NO_EVIDENCE_MODEL.name(),
                null,
                false,
                0,
                0,
                false,
                0,
                List.of(),
                true,
                false,
                3L,
                0,
                true,
                Map.of(),
                12000,
                10,
                false,
                List.of(),
                false,
                "",
                false
        );
        return new ContextAssemblyResult(
                "context",
                List.of(new PromptMessage("user", "根据知识库回答")),
                List.of(),
                trace,
                true,
                false,
                false,
                "",
                List.<KnowledgeBaseRagEvidenceCitation>of()
        );
    }

    private ContextAssemblyResult contextResultWithCitation() {
        ContextTrace trace = new ContextTrace(
                10L,
                null,
                "AGENT_MEMORY",
                GroundingPolicy.AUTO_RAG.name(),
                RouteDecision.AUTO_RAG_EVIDENCE.name(),
                null,
                false,
                0,
                0,
                false,
                0,
                List.of(),
                true,
                false,
                3L,
                1,
                false,
                Map.of(83L, 1),
                12000,
                10,
                false,
                List.of(),
                false,
                "",
                false
        );
        return new ContextAssemblyResult(
                "context",
                List.of(new PromptMessage("user", "P1 SLA?")),
                List.of(),
                trace,
                true,
                false,
                false,
                "",
                List.of(citation())
        );
    }

    private ContextAssemblyResult autoRequiredNoEvidenceContextResult() {
        ContextTrace trace = new ContextTrace(
                10L,
                null,
                "AGENT_MEMORY",
                GroundingPolicy.AUTO_RAG.name(),
                RouteDecision.AUTO_REQUIRED_NO_EVIDENCE_FALLBACK.name(),
                null,
                false,
                0,
                0,
                false,
                0,
                List.of(),
                true,
                true,
                3L,
                0,
                true,
                Map.of(),
                12000,
                10,
                false,
                List.of(),
                true,
                "REQUIRED_EVIDENCE_NO_EVIDENCE",
                true
        );
        return new ContextAssemblyResult(
                "context",
                List.of(new PromptMessage("user", "请引用文档回答")),
                List.of(),
                trace,
                true,
                true,
                true,
                "当前知识库中没有找到足够证据，无法基于知识库回答该问题。",
                List.<KnowledgeBaseRagEvidenceCitation>of()
        );
    }
}
