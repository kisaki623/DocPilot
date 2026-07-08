package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.rag.RagRetrievalHit;
import com.docpilot.backend.ai.rag.RagRetrievalQuery;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.service.RagDocumentRetrievalService;
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
class DocumentSearchToolTest {

    @Mock
    private RagDocumentRetrievalService retrievalService;

    @Test
    void shouldRetrieveEvidenceAndReturnSafeSearchResult() {
        DocumentSearchTool tool = new DocumentSearchTool(retrievalService);
        RagRetrievalHit hit = new RagRetrievalHit(
                1,
                "vec-1",
                0.91d,
                7L,
                101L,
                "Ops Playbook",
                2,
                300L,
                4,
                "Cache warmup runbook says Redis keeps session cache during deploy. "
                        + "This fixture contains enough text to verify the search tool only exposes bounded previews. "
                        + "The remaining private operational details should be truncated before they leave the tool result.",
                "hash-1",
                20,
                160,
                24,
                "mock-embedding",
                "Operations > Cache",
                "SECTION",
                3,
                "page=3#block=2",
                "PARAGRAPH"
        );
        RagRetrievalResult retrieval = new RagRetrievalResult(
                7L,
                101L,
                "cache warmup",
                4,
                2,
                List.of(hit),
                List.of(hit.toCitation()),
                false,
                "mock",
                "test_collection",
                "mock-embedding"
        );
        when(retrievalService.retrieve(new RagRetrievalQuery(7L, 101L, "cache warmup", 4, 2, "")))
                .thenReturn(retrieval);

        DocumentSearchTool.SearchResult result = tool.execute(new DocumentSearchTool.SearchInput(
                7L,
                101L,
                "cache warmup",
                4,
                2
        ));

        ArgumentCaptor<RagRetrievalQuery> queryCaptor = ArgumentCaptor.forClass(RagRetrievalQuery.class);
        verify(retrievalService).retrieve(queryCaptor.capture());
        assertThat(queryCaptor.getValue().userId()).isEqualTo(7L);
        assertThat(queryCaptor.getValue().documentId()).isEqualTo(101L);
        assertThat(queryCaptor.getValue().query()).isEqualTo("cache warmup");
        assertThat(result.noEvidence()).isFalse();
        assertThat(result.hitCount()).isEqualTo(1);
        assertThat(result.citationCount()).isEqualTo(1);
        assertThat(result.hits().get(0).sourceLocator()).isEqualTo("page=3#block=2");
        assertThat(result.hits().get(0).blockType()).isEqualTo("PARAGRAPH");
        assertThat(result.hits().get(0).contentHash()).isEqualTo("hash-1");
        assertThat(result.hits().get(0).snippet()).hasSizeLessThanOrEqualTo(183);
        assertThat(result.hits().get(0).snippet()).doesNotContain("remaining private operational details");
        assertThat(result.citations().get(0).quoteText()).isNotBlank();
        assertThat(result.outputSummary())
                .contains("hitCount=1")
                .contains("citationCount=1")
                .contains("noEvidence=false");
    }

    @Test
    void shouldReturnNoEvidenceSummary() {
        DocumentSearchTool tool = new DocumentSearchTool(retrievalService);
        when(retrievalService.retrieve(new RagRetrievalQuery(7L, 101L, "missing topic", null, null, "")))
                .thenReturn(new RagRetrievalResult(
                        7L,
                        101L,
                        "missing topic",
                        3,
                        1,
                        List.of(),
                        List.of(),
                        true,
                        "mock",
                        "test_collection",
                        "mock-embedding"
                ));

        DocumentSearchTool.SearchResult result = tool.execute(new DocumentSearchTool.SearchInput(
                7L,
                101L,
                "missing topic",
                null,
                null
        ));

        assertThat(result.noEvidence()).isTrue();
        assertThat(result.hitCount()).isZero();
        assertThat(result.citations()).isEmpty();
        assertThat(result.outputSummary()).contains("noEvidence=true");
    }

    @Test
    void shouldPropagateScopeRejectionFromRetrievalService() {
        DocumentSearchTool tool = new DocumentSearchTool(retrievalService);
        when(retrievalService.retrieve(new RagRetrievalQuery(7L, 101L, "forbidden", null, null, "")))
                .thenThrow(new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN));

        BusinessException ex = assertThrows(BusinessException.class, () -> tool.execute(new DocumentSearchTool.SearchInput(
                7L,
                101L,
                "forbidden",
                null,
                null
        )));

        assertEquals(ErrorCode.DOCUMENT_FORBIDDEN, ex.getErrorCode());
    }
}
