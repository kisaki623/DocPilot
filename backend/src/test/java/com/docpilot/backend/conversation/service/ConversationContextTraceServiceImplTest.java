package com.docpilot.backend.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.docpilot.backend.ai.context.ContextTrace;
import com.docpilot.backend.ai.context.ContextTraceTechnicalDetails;
import com.docpilot.backend.ai.context.GroundingPolicy;
import com.docpilot.backend.ai.context.RouteDecision;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;
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
        assertThat(entity.getCitationsJson()).contains("SLA Guide");
        assertThat(entity.getTechnicalDetailsJson()).contains("ctx-10-102");
        assertThat(entity.getTechnicalDetailsJson()).contains("scoreRows");
        assertThat(entity.getTechnicalDetailsJson()).doesNotContain("P1 incidents respond within 10 minutes");
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
        assertThat(trace.citations()).hasSize(1);
        assertThat(trace.citations().get(0).documentTitle()).isEqualTo("SLA Guide");
        assertThat(trace.citations().get(0).sourceLocator()).isEqualTo("page:2#block:5");
        assertThat(trace.technicalDetails().available()).isTrue();
        assertThat(trace.technicalDetails().traceId()).isEqualTo("ctx-10-102");
        assertThat(trace.technicalDetails().retrieval().scoreRows()).hasSize(1);
        assertThat(trace.technicalDetails().retrieval().scoreRows().get(0).documentTitle()).isEqualTo("SLA Guide");
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
    void shouldTreatMissingCitationsJsonAsEmptyForOldTraceRows() {
        ConversationContextTrace entity = entity();
        entity.setCitationsJson(null);
        when(traceMapper.selectByMessage(7L, 10L, 102L)).thenReturn(entity);

        ContextTrace trace = service.getByMessage(7L, 10L, 102L);

        assertThat(trace.citations()).isEmpty();
    }

    @Test
    void shouldTreatMissingTechnicalDetailsJsonAsUnavailableForOldTraceRows() {
        ConversationContextTrace entity = entity();
        entity.setTechnicalDetailsJson(null);
        when(traceMapper.selectByMessage(7L, 10L, 102L)).thenReturn(entity);

        ContextTrace trace = service.getByMessage(7L, 10L, 102L);

        assertThat(trace.technicalDetails().available()).isFalse();
        assertThat(trace.technicalDetails().traceId()).isEqualTo("ctx-10-102");
    }

    @Test
    void shouldExposeDerivedSourceBreakdownWithoutPersistingPromptContent() throws Exception {
        String json = new ObjectMapper().writeValueAsString(trace());

        assertThat(json).contains("contextSourceCounts");
        assertThat(json).contains("contextSourceFlags");
        assertThat(json).contains("technicalDetails");
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
        ).withTechnicalDetails(technicalDetails()).withCitations(List.of(citation()));
    }

    private ContextTraceTechnicalDetails technicalDetails() {
        return ContextTraceTechnicalDetails.build(
                10L,
                102L,
                new ContextTraceTechnicalDetails.RouteDetails(
                        GroundingPolicy.AUTO_RAG.name(),
                        RouteDecision.AUTO_RAG_EVIDENCE.name(),
                        "AUTO_RAG_EVIDENCE",
                        true,
                        true,
                        false,
                        true,
                        false
                ),
                Map.of("retrieval", 12L, "modelCall", 24L),
                new ContextTraceTechnicalDetails.RetrievalDetails(
                        "hybrid",
                        "qdrant",
                        6,
                        2,
                        Map.of(83L, 2),
                        true,
                        "qwen3-rerank",
                        "",
                        true,
                        2,
                        1,
                        ContextTraceTechnicalDetails.EvidenceGateDetails.passed("EVIDENCE_SELECTED"),
                        List.of(new ContextTraceTechnicalDetails.ScoreRow(
                                1,
                                83L,
                                "SLA Guide",
                                8301L,
                                2,
                                "page:2#block:5",
                                0.91D,
                                null,
                                null,
                                null,
                                0.91D,
                                true
                        ))
                ),
                new ContextTraceTechnicalDetails.TokenBudgetDetails(
                        12000,
                        120,
                        true,
                        List.of(new ContextTraceTechnicalDetails.TokenBudgetTypeSummary(
                                "MEMORY",
                                1,
                                20,
                                1,
                                10
                        )),
                        List.of(new ContextTraceTechnicalDetails.TokenDroppedReason(
                                "MEMORY",
                                1,
                                "TOKEN_BUDGET_EXCEEDED"
                        ))
                ),
                new ContextTraceTechnicalDetails.ContextUsageDetails(
                        new ContextTraceTechnicalDetails.SummaryUsage(true),
                        new ContextTraceTechnicalDetails.MemoryUsage(true, 1, List.of("PREFERENCE")),
                        new ContextTraceTechnicalDetails.RecentUsage(2, 4)
                ),
                new ContextTraceTechnicalDetails.FallbackDetails(false, "", "")
        );
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
        entity.setCitationsJson("""
                [{
                  "index":1,
                  "knowledgeBaseId":3,
                  "documentId":83,
                  "documentTitle":"SLA Guide",
                  "indexVersion":1,
                  "chunkId":8301,
                  "chunkIndex":2,
                  "startOffset":10,
                  "endOffset":120,
                  "contentHash":"hash-83",
                  "snippet":"P1 incidents respond within 10 minutes.",
                  "quoteText":"P1 incidents respond within 10 minutes.",
                  "quoteStartOffset":10,
                  "quoteEndOffset":50,
                  "sectionPath":"SLA / P1",
                  "structureType":"paragraph",
                  "pageNumber":2,
                  "sourceLocator":"page:2#block:5",
                  "blockType":"PAGE",
                  "score":0.91,
                  "vectorScore":0.91
                }]
                """);
        entity.setTechnicalDetailsJson("""
                {
                  "available":true,
                  "traceId":"ctx-10-102",
                  "messageId":102,
                  "route":{
                    "groundingPolicy":"AUTO_RAG",
                    "routeDecision":"AUTO_RAG_EVIDENCE",
                    "routeReason":"AUTO_RAG_EVIDENCE",
                    "ragTriggered":true,
                    "ragRequired":true,
                    "noEvidence":false,
                    "llmCalled":true,
                    "modelSkipped":false
                  },
                  "timingsMs":{"retrieval":12,"modelCall":24},
                  "retrieval":{
                    "retrievalMode":"hybrid",
                    "provider":"qdrant",
                    "topK":6,
                    "evidenceCount":2,
                    "documentHitCounts":{"83":2},
                    "rerankApplied":true,
                    "rerankModel":"qwen3-rerank",
                    "rerankFailureReason":"",
                    "multiQueryApplied":true,
                    "queryVariantCount":2,
                    "queryDedupeCount":1,
                    "evidenceGate":{"status":"PASSED","reason":"EVIDENCE_SELECTED"},
                    "scoreRows":[{
                      "citationIndex":1,
                      "documentId":83,
                      "documentTitle":"SLA Guide",
                      "chunkId":8301,
                      "chunkIndex":2,
                      "locator":"page:2#block:5",
                      "vectorScore":0.91,
                      "finalScore":0.91,
                      "selectedAsCitation":true
                    }]
                  },
                  "tokenBudget":{
                    "maxPromptTokens":12000,
                    "estimatedPromptTokens":120,
                    "truncated":true,
                    "byType":[{"type":"MEMORY","usedCount":1,"usedTokens":20,"droppedCount":1,"droppedTokens":10}],
                    "droppedReasons":[{"type":"MEMORY","count":1,"reason":"TOKEN_BUDGET_EXCEEDED"}]
                  },
                  "contextUsage":{
                    "summary":{"used":true},
                    "memory":{"used":true,"count":1,"types":["PREFERENCE"]},
                    "recent":{"turnCount":2,"messageCount":4}
                  },
                  "fallback":{"used":false,"reason":"","safeError":""}
                }
                """);
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
