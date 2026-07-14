package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.rag.RagEvidenceCitation;
import com.docpilot.backend.ai.rag.RagQaAnswer;
import com.docpilot.backend.ai.rag.RagQaQuery;
import com.docpilot.backend.ai.rag.RagRetrievalHit;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.service.RagQaService;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentRagQaToolTest {

    @Mock
    private RagQaService ragQaService;

    @Test
    void shouldCallRagQaServiceAndReturnRetrievalTrace() {
        DocumentRagQaTool tool = new DocumentRagQaTool(ragQaService);
        RagRetrievalHit hit = new RagRetrievalHit(
                1,
                "vec-1",
                0.88d,
                100L,
                200L,
                2,
                300L,
                0,
                "Redis cache evidence",
                "hash-1",
                10,
                30,
                4,
                "mock-embedding"
        );
        RagEvidenceCitation citation = hit.toCitation();
        RagRetrievalResult retrieval = new RagRetrievalResult(
                100L,
                200L,
                "Where is cache stored?",
                4,
                2,
                List.of(hit),
                List.of(citation),
                false,
                "mock",
                "test_collection",
                "mock-embedding"
        );
        when(ragQaService.answer(new RagQaQuery(100L, 200L, "Where is cache stored?", 4, 2, "sess-1")))
                .thenReturn(new RagQaAnswer(
                        100L,
                        200L,
                        "Where is cache stored?",
                        "Cache is stored in Redis. [1]",
                        "sess-1",
                        retrieval,
                        false,
                        false,
                        ""
                ));

        DocumentRagQaTool.RagQaResult result = tool.execute(new DocumentRagQaTool.RagQaInput(
                100L,
                200L,
                "Where is cache stored?",
                "sess-1",
                4,
                2
        ));

        ArgumentCaptor<RagQaQuery> queryCaptor = ArgumentCaptor.forClass(RagQaQuery.class);
        verify(ragQaService).answer(queryCaptor.capture());
        assertThat(queryCaptor.getValue().userId()).isEqualTo(100L);
        assertThat(queryCaptor.getValue().documentId()).isEqualTo(200L);
        assertThat(queryCaptor.getValue().topK()).isEqualTo(4);
        assertThat(queryCaptor.getValue().indexVersion()).isEqualTo(2);
        assertThat(result.answer()).isEqualTo("Cache is stored in Redis. [1]");
        assertThat(result.retrievalHits()).containsExactly(hit);
        assertThat(result.citations()).containsExactly(citation);
        assertThat(result.outputSummary())
                .contains("hitCount=1")
                .contains("citationCount=1")
                .contains("noEvidence=false")
                .contains("fallbackUsed=false");
    }

    @Test
    void shouldReturnNoEvidenceFallbackSummary() {
        DocumentRagQaTool tool = new DocumentRagQaTool(ragQaService);
        RagRetrievalResult retrieval = new RagRetrievalResult(
                100L,
                200L,
                "missing topic",
                3,
                1,
                List.of(),
                List.of(),
                true,
                "mock",
                "test_collection",
                "mock-embedding"
        );
        when(ragQaService.answer(new RagQaQuery(100L, 200L, "missing topic", null, null, "")))
                .thenReturn(new RagQaAnswer(
                        100L,
                        200L,
                        "missing topic",
                        "未在当前文档索引中检索到足够证据",
                        "",
                        retrieval,
                        true,
                        true,
                        "no_evidence"
                ));

        DocumentRagQaTool.RagQaResult result = tool.execute(new DocumentRagQaTool.RagQaInput(
                100L,
                200L,
                "missing topic",
                "",
                null,
                null
        ));

        assertThat(result.noEvidence()).isTrue();
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.citations()).isEmpty();
        assertThat(result.retrievalHits()).isEmpty();
        assertThat(result.outputSummary())
                .contains("noEvidence=true")
                .contains("fallbackUsed=true")
                .contains("fallbackReason=no_evidence");
    }

    @Test
    void shouldPropagateScopeRejectionFromRagQaService() {
        DocumentRagQaTool tool = new DocumentRagQaTool(ragQaService);
        when(ragQaService.answer(new RagQaQuery(100L, 200L, "forbidden", null, null, "")))
                .thenThrow(new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN));

        BusinessException ex = assertThrows(BusinessException.class, () -> tool.execute(new DocumentRagQaTool.RagQaInput(
                100L,
                200L,
                "forbidden",
                "",
                null,
                null
        )));

        assertEquals(ErrorCode.DOCUMENT_FORBIDDEN, ex.getErrorCode());
    }
}
