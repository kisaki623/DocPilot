package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaAnswer;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
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
        verify(aiAnswerService, never()).answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString());
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
}
