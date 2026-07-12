package com.docpilot.backend.ai.context;

import com.docpilot.backend.ai.context.builder.KnowledgeBaseEvidenceBuilder;
import com.docpilot.backend.ai.context.builder.KnowledgeBaseEvidenceResult;
import com.docpilot.backend.ai.context.token.TokenEstimator;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.service.KnowledgeBaseRagRetrievalService;
import com.docpilot.backend.conversation.constant.ConversationContextMode;
import com.docpilot.backend.conversation.entity.Conversation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeBaseEvidenceContextBuilderTest {

    private final KnowledgeBaseRagRetrievalService retrievalService = mock(KnowledgeBaseRagRetrievalService.class);
    private final KnowledgeBaseEvidenceBuilder builder = new KnowledgeBaseEvidenceBuilder(
            retrievalService,
            new TokenEstimator()
    );

    @Test
    void shouldTriggerAutoRagForChineseKnowledgeBaseIntent() {
        KnowledgeBaseRagRetrievalHit hit = hit("vector-1", "Doc A", "evidence content");
        KnowledgeBaseRagEvidenceCitation citation = hit.toCitation();
        when(retrievalService.retrieve(any())).thenReturn(result(List.of(hit), List.of(citation), Map.of(101L, 1)));

        KnowledgeBaseEvidenceResult result = builder.build(
                conversation(3L),
                "\u6839\u636e\u77e5\u8bc6\u5e93\u56de\u7b54",
                ContextPolicy.forMode(ConversationContextMode.AGENT_MEMORY, null)
        );

        assertThat(result.triggered()).isTrue();
        assertThat(result.required()).isFalse();
        assertThat(result.noEvidence()).isFalse();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).type()).isEqualTo(ContextType.RAG_EVIDENCE);
        assertThat(result.items().get(0).content()).contains("documentTitle=Doc A", "evidence content");
        assertThat(result.citations()).containsExactly(citation);
        assertThat(result.documentHitCounts()).containsEntry(101L, 1);
        assertThat(result.routeDecision()).isEqualTo(RouteDecision.AUTO_RAG_EVIDENCE);

        ArgumentCaptor<KnowledgeBaseRagRetrievalQuery> captor =
                ArgumentCaptor.forClass(KnowledgeBaseRagRetrievalQuery.class);
        verify(retrievalService).retrieve(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(7L);
        assertThat(captor.getValue().knowledgeBaseId()).isEqualTo(3L);
        assertThat(captor.getValue().query()).isEqualTo("\u6839\u636e\u77e5\u8bc6\u5e93\u56de\u7b54");
        assertThat(captor.getValue().topK()).isEqualTo(6);
    }

    @Test
    void shouldTriggerOptionalRagWithoutFallbackForProjectStatusIntent() {
        when(retrievalService.retrieve(any())).thenReturn(result(List.of(), List.of(), Map.of()));

        KnowledgeBaseEvidenceResult result = builder.build(
                conversation(3L),
                "project status update",
                ContextPolicy.forMode(ConversationContextMode.AGENT_MEMORY, null)
        );

        assertThat(result.triggered()).isTrue();
        assertThat(result.required()).isFalse();
        assertThat(result.noEvidence()).isTrue();
        assertThat(result.fallbackAnswer()).isBlank();
        assertThat(result.items()).isEmpty();
        assertThat(result.routeDecision()).isEqualTo(RouteDecision.AUTO_NO_EVIDENCE_MODEL);
        verify(retrievalService).retrieve(any());
    }

    @Test
    void autoRagShouldNotTriggerRetrievalForGeneralQuestion() {
        KnowledgeBaseEvidenceResult result = builder.build(
                conversation(3L),
                "太阳为什么会发光",
                ContextPolicy.forMode(ConversationContextMode.AGENT_MEMORY, null, true),
                GroundingPolicy.AUTO_RAG
        );

        assertThat(result.triggered()).isFalse();
        assertThat(result.required()).isFalse();
        assertThat(result.noEvidence()).isFalse();
        assertThat(result.routeDecision()).isEqualTo(RouteDecision.AUTO_INTENT_NOT_TRIGGERED_MODEL);
        verifyNoInteractions(retrievalService);
    }

    @Test
    void autoRagShouldNotTreatKnowledgeBaseConceptQuestionAsRetrievalIntent() {
        KnowledgeBaseEvidenceResult englishResult = builder.build(
                conversation(3L),
                "What is a knowledge base?",
                ContextPolicy.forMode(ConversationContextMode.AGENT_MEMORY, null, true),
                GroundingPolicy.AUTO_RAG
        );
        KnowledgeBaseEvidenceResult chineseResult = builder.build(
                conversation(3L),
                "知识库是什么？",
                ContextPolicy.forMode(ConversationContextMode.AGENT_MEMORY, null, true),
                GroundingPolicy.AUTO_RAG
        );

        assertThat(englishResult.triggered()).isFalse();
        assertThat(englishResult.routeDecision()).isEqualTo(RouteDecision.AUTO_INTENT_NOT_TRIGGERED_MODEL);
        assertThat(chineseResult.triggered()).isFalse();
        assertThat(chineseResult.routeDecision()).isEqualTo(RouteDecision.AUTO_INTENT_NOT_TRIGGERED_MODEL);
        verifyNoInteractions(retrievalService);
    }

    @Test
    void modelOnlyShouldNotTriggerRetrievalEvenWhenKnowledgeBaseIsBound() {
        KnowledgeBaseEvidenceResult result = builder.build(
                conversation(3L),
                "根据知识库回答",
                ContextPolicy.forMode(ConversationContextMode.AGENT_MEMORY, null, false),
                GroundingPolicy.MODEL_ONLY
        );

        assertThat(result.triggered()).isFalse();
        assertThat(result.required()).isFalse();
        assertThat(result.routeDecision()).isEqualTo(RouteDecision.MODEL_ONLY);
        verifyNoInteractions(retrievalService);
    }

    @Test
    void shouldSkipRetrievalWhenPolicyDisablesRag() {
        KnowledgeBaseEvidenceResult result = builder.build(
                conversation(3L),
                "\u6839\u636e\u77e5\u8bc6\u5e93\u56de\u7b54",
                ContextPolicy.forMode(ConversationContextMode.RECENT_TURNS, null)
        );

        assertThat(result.triggered()).isFalse();
        assertThat(result.items()).isEmpty();
        assertThat(result.routeDecision()).isEqualTo(RouteDecision.MODEL_ONLY);
        verifyNoInteractions(retrievalService);
    }

    @Test
    void autoRagShouldFallbackToModelWhenTriggeredRetrievalHasNoEvidence() {
        when(retrievalService.retrieve(any())).thenReturn(result(List.of(), List.of(), Map.of()));

        KnowledgeBaseEvidenceResult result = builder.build(
                conversation(3L),
                "\u6839\u636e\u77e5\u8bc6\u5e93\u56de\u7b54",
                ContextPolicy.forMode(ConversationContextMode.AGENT_MEMORY, null)
        );

        assertThat(result.triggered()).isTrue();
        assertThat(result.required()).isFalse();
        assertThat(result.noEvidence()).isTrue();
        assertThat(result.fallbackAnswer()).isBlank();
        assertThat(result.routeDecision()).isEqualTo(RouteDecision.AUTO_NO_EVIDENCE_MODEL);
    }

    @Test
    void strictKbShouldRefuseWhenNoEvidence() {
        when(retrievalService.retrieve(any())).thenReturn(result(List.of(), List.of(), Map.of()));

        KnowledgeBaseEvidenceResult result = builder.build(
                conversation(3L),
                "普通问题",
                ContextPolicy.forMode(ConversationContextMode.RECENT_TURNS, null, true),
                GroundingPolicy.STRICT_KB
        );

        assertThat(result.triggered()).isTrue();
        assertThat(result.required()).isTrue();
        assertThat(result.noEvidence()).isTrue();
        assertThat(result.fallbackAnswer()).contains("没有找到足够证据");
        assertThat(result.routeDecision()).isEqualTo(RouteDecision.STRICT_NO_EVIDENCE_FALLBACK);
    }

    @Test
    void strictKbShouldNotRefuseWhenConversationHasNoKnowledgeBase() {
        KnowledgeBaseEvidenceResult result = builder.build(
                conversation(null),
                "普通问题",
                ContextPolicy.forMode(ConversationContextMode.RECENT_TURNS, null, true),
                GroundingPolicy.STRICT_KB
        );

        assertThat(result.triggered()).isFalse();
        assertThat(result.required()).isFalse();
        assertThat(result.noEvidence()).isFalse();
        assertThat(result.fallbackAnswer()).isBlank();
        assertThat(result.routeDecision()).isEqualTo(RouteDecision.MODEL_ONLY);
        verifyNoInteractions(retrievalService);
    }

    @Test
    void shouldTruncateLongEvidenceBlocks() {
        String longContent = "x".repeat(260);
        when(retrievalService.retrieve(any()))
                .thenReturn(result(List.of(hit("vector-long", "Long Doc", longContent)), List.of(), Map.of(101L, 1)));

        ContextPolicy tinyEvidencePolicy = new ContextPolicy(
                ConversationContextMode.AGENT_MEMORY,
                12_000,
                8,
                5,
                1_200,
                6,
                6_000,
                1,
                true,
                true,
                true
        );

        KnowledgeBaseEvidenceResult result = builder.build(
                conversation(3L),
                "based on the document",
                tinyEvidencePolicy
        );

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).content()).endsWith("...");
        assertThat(result.items().get(0).content()).doesNotContain(longContent);
    }

    private Conversation conversation(Long knowledgeBaseId) {
        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setUserId(7L);
        conversation.setBoundKnowledgeBaseId(knowledgeBaseId);
        conversation.setContextMode(ConversationContextMode.AGENT_MEMORY);
        conversation.setStatus("ACTIVE");
        return conversation;
    }

    private KnowledgeBaseRagRetrievalResult result(List<KnowledgeBaseRagRetrievalHit> hits,
                                                   List<KnowledgeBaseRagEvidenceCitation> citations,
                                                   Map<Long, Integer> documentHitCounts) {
        return new KnowledgeBaseRagRetrievalResult(
                7L,
                3L,
                "query",
                6,
                1,
                List.of(101L),
                hits,
                citations,
                hits.isEmpty(),
                "mock",
                "collection",
                "embedding-model",
                documentHitCounts
        );
    }

    private KnowledgeBaseRagRetrievalHit hit(String vectorId, String title, String content) {
        return new KnowledgeBaseRagRetrievalHit(
                1,
                3L,
                vectorId,
                0.91,
                7L,
                101L,
                title,
                1,
                501L,
                0,
                content,
                "hash",
                0,
                content.length(),
                content.length(),
                "embedding-model"
        );
    }
}
