package com.docpilot.backend.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.docpilot.backend.ai.context.ContextTrace;
import com.docpilot.backend.ai.context.GroundingPolicy;
import com.docpilot.backend.ai.context.RouteDecision;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.conversation.entity.ConversationContextTrace;
import com.docpilot.backend.conversation.mapper.ConversationContextTraceMapper;
import com.docpilot.backend.conversation.service.impl.ConversationContextTraceServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationContextTraceServiceImplTest {

    private final ConversationContextTraceMapper traceMapper = mock(ConversationContextTraceMapper.class);
    private final ConversationService conversationService = mock(ConversationService.class);
    private final ConversationContextTraceServiceImpl service = new ConversationContextTraceServiceImpl(
            traceMapper,
            conversationService,
            new ObjectMapper()
    );

    @Test
    void shouldPersistTraceAsSummaryJsonWithoutPromptContent() {
        when(traceMapper.insert(any(ConversationContextTrace.class))).thenReturn(1);
        ContextTrace trace = trace();

        service.save(7L, trace);

        ArgumentCaptor<ConversationContextTrace> captor = ArgumentCaptor.forClass(ConversationContextTrace.class);
        verify(traceMapper).insert(captor.capture());
        ConversationContextTrace entity = captor.getValue();
        assertThat(entity.getUserId()).isEqualTo(7L);
        assertThat(entity.getMessageId()).isEqualTo(102L);
        assertThat(entity.getGroundingPolicy()).isEqualTo(GroundingPolicy.AUTO_RAG.name());
        assertThat(entity.getRouteDecision()).isEqualTo(RouteDecision.AUTO_RAG_EVIDENCE.name());
        assertThat(entity.getLlmCalled()).isTrue();
        assertThat(entity.getMemoryTypesJson()).contains("PREFERENCE");
        assertThat(entity.getDocumentHitCountsJson()).contains("83");
        assertThat(entity.getTruncatedTypesJson()).contains("MEMORY");
    }

    @Test
    void shouldReadTraceByMessageAfterConversationOwnershipCheck() {
        ConversationContextTrace entity = entity();
        when(traceMapper.selectByMessage(7L, 10L, 102L)).thenReturn(entity);

        ContextTrace trace = service.getByMessage(7L, 10L, 102L);

        verify(conversationService).requireOwnedActive(7L, 10L);
        assertThat(trace.conversationId()).isEqualTo(10L);
        assertThat(trace.messageId()).isEqualTo(102L);
        assertThat(trace.memoryTypes()).containsExactly("PREFERENCE");
        assertThat(trace.groundingPolicy()).isEqualTo(GroundingPolicy.AUTO_RAG.name());
        assertThat(trace.routeDecision()).isEqualTo(RouteDecision.AUTO_RAG_EVIDENCE.name());
        assertThat(trace.llmCalled()).isTrue();
        assertThat(trace.documentHitCounts()).containsEntry(83L, 2);
        assertThat(trace.truncatedTypes()).containsExactly("MEMORY");
        assertThat(trace.getContextSourceCounts())
                .containsEntry("conversationSummary", 1)
                .containsEntry("recentMessages", 4)
                .containsEntry("userMemory", 1)
                .containsEntry("ragEvidence", 2);
        assertThat(trace.getContextSourceFlags())
                .containsEntry("conversationContext", true)
                .containsEntry("userMemory", true)
                .containsEntry("ragEvidence", true);
        assertThat(trace.getContextSourceFlags()).containsEntry("llmCalled", true);
    }

    @Test
    void shouldReadTracesByMessagesAfterConversationOwnershipCheck() {
        ConversationContextTrace entity = entity();
        when(traceMapper.selectByMessages(7L, 10L, List.of(102L))).thenReturn(List.of(entity));

        Map<Long, ContextTrace> traces = service.listByMessages(7L, 10L, java.util.Arrays.asList(102L, 102L, null));

        verify(conversationService).requireOwnedActive(7L, 10L);
        assertThat(traces).containsOnlyKeys(102L);
        assertThat(traces.get(102L).routeDecision()).isEqualTo(RouteDecision.AUTO_RAG_EVIDENCE.name());
    }

    @Test
    void shouldExposeDerivedSourceBreakdownWithoutPersistingPromptContent() throws Exception {
        String json = new ObjectMapper().writeValueAsString(trace());

        assertThat(json).contains("contextSourceCounts");
        assertThat(json).contains("contextSourceFlags");
        assertThat(json).contains("ragEvidence");
        assertThat(json).doesNotContain("prompt");
        assertThat(json).doesNotContain("doc evidence");
    }

    @Test
    void shouldFailWhenTraceMissing() {
        when(traceMapper.selectByMessage(7L, 10L, 102L)).thenReturn(null);

        assertThatThrownBy(() -> service.getByMessage(7L, 10L, 102L))
                .isInstanceOf(BusinessException.class);
    }

    private ContextTrace trace() {
        return new ContextTrace(
                10L,
                102L,
                "AGENT_MEMORY",
                GroundingPolicy.AUTO_RAG.name(),
                RouteDecision.AUTO_RAG_EVIDENCE.name(),
                true,
                true,
                2,
                4,
                true,
                1,
                List.of("PREFERENCE"),
                true,
                true,
                3L,
                2,
                false,
                Map.of(83L, 2),
                12000,
                120,
                true,
                List.of("MEMORY"),
                false,
                "",
                false
        );
    }

    private ConversationContextTrace entity() {
        ConversationContextTrace entity = new ConversationContextTrace();
        entity.setConversationId(10L);
        entity.setMessageId(102L);
        entity.setUserId(7L);
        entity.setContextMode("AGENT_MEMORY");
        entity.setGroundingPolicy(GroundingPolicy.AUTO_RAG.name());
        entity.setRouteDecision(RouteDecision.AUTO_RAG_EVIDENCE.name());
        entity.setLlmCalled(true);
        entity.setSummaryUsed(true);
        entity.setRecentTurnCount(2);
        entity.setRecentMessageCount(4);
        entity.setMemoryUsed(true);
        entity.setMemoryCount(1);
        entity.setMemoryTypesJson("[\"PREFERENCE\"]");
        entity.setRagTriggered(true);
        entity.setRagRequired(true);
        entity.setKnowledgeBaseId(3L);
        entity.setEvidenceCount(2);
        entity.setNoEvidence(false);
        entity.setDocumentHitCountsJson("{\"83\":2}");
        entity.setMaxPromptTokens(12000);
        entity.setEstimatedPromptTokens(120);
        entity.setTruncated(true);
        entity.setTruncatedTypesJson("[\"MEMORY\"]");
        entity.setFallbackUsed(false);
        entity.setFallbackReason("");
        entity.setModelCallSkipped(false);
        return entity;
    }
}
