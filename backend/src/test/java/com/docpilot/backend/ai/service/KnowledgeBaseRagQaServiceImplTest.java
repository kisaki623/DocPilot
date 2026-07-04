package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaAnswer;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.service.impl.KnowledgeBaseRagQaServiceImpl;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseRagQaServiceImplTest {

    private final KnowledgeBaseRagRetrievalService retrievalService = mock(KnowledgeBaseRagRetrievalService.class);
    private final AiAnswerService aiAnswerService = mock(AiAnswerService.class);
    private final RagQaProperties ragQaProperties = new RagQaProperties();
    private final KnowledgeBaseRagQaServiceImpl service = new KnowledgeBaseRagQaServiceImpl(
            retrievalService,
            aiAnswerService,
            ragQaProperties
    );

    @Test
    void shouldAnswerWithEvidenceAndModelMetadata() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(retrieval(false));
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn("Use Redis [1].");
        when(aiAnswerService.provider()).thenReturn("mock");
        when(aiAnswerService.model()).thenReturn("mock-model");

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "cache?",
                3,
                1,
                "s1"
        ));

        assertThat(answer.answer()).isEqualTo("Use Redis [1].");
        assertThat(answer.noEvidence()).isFalse();
        assertThat(answer.retrieval().citations()).hasSize(1);
        assertThat(answer.answerProvider()).isEqualTo("mock");
        assertThat(answer.answerModel()).isEqualTo("mock-model");
        assertThat(answer.modelCallCount()).isEqualTo(1);
        assertThat(answer.audit().grounded()).isTrue();
        assertThat(answer.audit().evidenceCount()).isEqualTo(1);
        assertThat(answer.audit().citationCount()).isEqualTo(1);
        assertThat(answer.audit().scoreSummary().min()).isEqualTo(0.9D);
        assertThat(answer.audit().documentHitCounts()).containsEntry(101L, 1);
    }

    @Test
    void shouldNotCallModelWhenNoEvidence() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(retrieval(true));

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "missing?",
                3,
                1,
                ""
        ));

        assertThat(answer.noEvidence()).isTrue();
        assertThat(answer.answer()).contains("未在当前知识库索引中检索到足够证据");
        assertThat(answer.modelCallCount()).isZero();
        assertThat(answer.audit().grounded()).isFalse();
        assertThat(answer.audit().citationCount()).isZero();
        assertThat(answer.audit().fallbackReason()).isEqualTo("no_evidence");
        verify(aiAnswerService, never()).answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString());
    }

    @Test
    void shouldFallbackWithoutCallingModelWhenRetrievalFails() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenThrow(new IllegalStateException("vector down"));

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "question",
                3,
                1,
                ""
        ));

        assertThat(answer.fallbackUsed()).isTrue();
        assertThat(answer.fallbackReason()).isEqualTo("retrieval_unavailable");
        assertThat(answer.modelCallCount()).isZero();
        assertThat(answer.audit().grounded()).isFalse();
        assertThat(answer.audit().evidenceCount()).isZero();
        assertThat(answer.audit().fallbackReason()).isEqualTo("retrieval_unavailable");
        verify(aiAnswerService, never()).answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString());
    }

    @Test
    void shouldFallbackWithEvidenceWhenAnswerGenerationFails() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(retrieval(false));
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenThrow(new IllegalStateException("model timeout"));
        when(aiAnswerService.provider()).thenReturn("real");
        when(aiAnswerService.model()).thenReturn("real-model");

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "summary?",
                3,
                1,
                "s1"
        ));

        assertThat(answer.noEvidence()).isFalse();
        assertThat(answer.fallbackUsed()).isTrue();
        assertThat(answer.fallbackReason()).isEqualTo("answer_generation_failed");
        assertThat(answer.answer()).contains("回答模型本次生成失败");
        assertThat(answer.retrieval().citations()).hasSize(1);
        assertThat(answer.answerProvider()).isEqualTo("real");
        assertThat(answer.answerModel()).isEqualTo("real-model");
        assertThat(answer.modelCallCount()).isEqualTo(1);
        assertThat(answer.audit().grounded()).isFalse();
        assertThat(answer.audit().citationCount()).isEqualTo(1);
        assertThat(answer.audit().fallbackReason()).isEqualTo("answer_generation_failed");
    }

    @Test
    void shouldNotMaskScopeExceptionWithFallback() {
        when(retrievalService.retrieve(org.mockito.Mockito.any()))
                .thenThrow(new BusinessException(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "question",
                3,
                1,
                ""
        )));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void shouldPassMultiQueryOverrideToRetrieval() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(retrieval(true));

        service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "compare cache and vector retention",
                4,
                1,
                "s1",
                true,
                4
        ));

        org.mockito.ArgumentCaptor<KnowledgeBaseRagRetrievalQuery> captor =
                org.mockito.ArgumentCaptor.forClass(KnowledgeBaseRagRetrievalQuery.class);
        verify(retrievalService).retrieve(captor.capture());
        assertThat(captor.getValue().multiQueryEnabled()).isTrue();
        assertThat(captor.getValue().maxQueryVariants()).isEqualTo(4);
    }

    @Test
    void shouldFilterNumericDistractorCitationsAfterAnswerGeneration() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(numericDistractorRetrieval());
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn("Invoice archive retention is 7 years [1].");

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "How long are invoice archive records retained?",
                2,
                1,
                "s1"
        ));

        assertThat(answer.retrieval().hits()).hasSize(2);
        assertThat(answer.retrieval().citations()).hasSize(1);
        assertThat(answer.retrieval().citations().get(0).documentId()).isEqualTo(101L);
        assertThat(answer.retrieval().citations().get(0).quoteText()).contains("7 years");
        assertThat(answer.audit().citationCount()).isEqualTo(1);
        assertThat(answer.audit().documentHitCounts()).containsEntry(101L, 1).containsEntry(102L, 1);
    }

    @Test
    void shouldKeepNonNumericSupportCitationWhenChunkContainsRunMarkerDigits() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(multiDocumentRetrievalWithRunMarker());
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn("The team paused background retries, and the P1 response target is 30 minutes [1][2].");

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "Summarize the checkout incident response and support SLA.",
                2,
                1,
                "s1"
        ));

        assertThat(answer.retrieval().citations()).hasSize(2);
        assertThat(answer.retrieval().citations()).extracting("documentId")
                .containsExactly(101L, 102L);
    }

    private KnowledgeBaseRagRetrievalResult retrieval(boolean noEvidence) {
        List<KnowledgeBaseRagRetrievalHit> hits = noEvidence ? List.of() : List.of(new KnowledgeBaseRagRetrievalHit(
                1,
                10L,
                "v1",
                0.9D,
                7L,
                101L,
                "Redis Guide",
                1,
                900L,
                0,
                "Redis stores cache.",
                "hash",
                0,
                19,
                4,
                "mock-model"
        ));
        return new KnowledgeBaseRagRetrievalResult(
                7L,
                10L,
                "question",
                3,
                1,
                List.of(101L),
                hits,
                hits.stream().map(KnowledgeBaseRagRetrievalHit::toCitation).toList(),
                noEvidence,
                "in_memory",
                "",
                "mock-model",
                Map.of(101L, hits.size())
        );
    }

    private KnowledgeBaseRagRetrievalResult numericDistractorRetrieval() {
        List<KnowledgeBaseRagRetrievalHit> hits = List.of(
                new KnowledgeBaseRagRetrievalHit(
                        1,
                        10L,
                        "invoice",
                        0.99D,
                        7L,
                        101L,
                        "Invoice Retention Policy",
                        1,
                        901L,
                        0,
                        "Invoice archive retention is 7 years. The archive owner is Finance Operations.",
                        "hash-invoice",
                        0,
                        80,
                        80,
                        "mock-model"
                ),
                new KnowledgeBaseRagRetrievalHit(
                        2,
                        10L,
                        "marketing",
                        0.88D,
                        7L,
                        102L,
                        "Marketing Draft Retention",
                        1,
                        902L,
                        0,
                        "Marketing campaign drafts are retained for 3 years. This document should not be used as invoice archive evidence.",
                        "hash-marketing",
                        0,
                        112,
                        112,
                        "mock-model"
                )
        );
        return new KnowledgeBaseRagRetrievalResult(
                7L,
                10L,
                "How long are invoice archive records retained?",
                2,
                1,
                List.of(101L, 102L),
                hits,
                hits.stream().map(KnowledgeBaseRagRetrievalHit::toCitation).toList(),
                false,
                "in_memory",
                "",
                "mock-model",
                Map.of(101L, 1, 102L, 1)
        );
    }

    private KnowledgeBaseRagRetrievalResult multiDocumentRetrievalWithRunMarker() {
        List<KnowledgeBaseRagRetrievalHit> hits = List.of(
                new KnowledgeBaseRagRetrievalHit(
                        1,
                        10L,
                        "incident",
                        0.96D,
                        7L,
                        101L,
                        "Checkout Incident Review",
                        1,
                        903L,
                        0,
                        "docpilot-rag-natural-corpus-20260704142549-252f85. Engineers paused background retries and drained the checkout worker queue.",
                        "hash-incident",
                        0,
                        130,
                        130,
                        "mock-model"
                ),
                new KnowledgeBaseRagRetrievalHit(
                        2,
                        10L,
                        "support",
                        0.95D,
                        7L,
                        102L,
                        "Support SLA Note",
                        1,
                        904L,
                        0,
                        "The P1 response target is 30 minutes when checkout cannot complete.",
                        "hash-support",
                        0,
                        72,
                        72,
                        "mock-model"
                )
        );
        return new KnowledgeBaseRagRetrievalResult(
                7L,
                10L,
                "Summarize the checkout incident response and support SLA.",
                2,
                1,
                List.of(101L, 102L),
                hits,
                hits.stream().map(KnowledgeBaseRagRetrievalHit::toCitation).toList(),
                false,
                "in_memory",
                "",
                "mock-model",
                Map.of(101L, 1, 102L, 1)
        );
    }
}
