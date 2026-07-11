package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.service.KnowledgeBaseRagRetrievalService;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseSearchToolTest {

    @Mock
    private KnowledgeBaseRagRetrievalService retrievalService;

    @Test
    void shouldRetrieveKnowledgeBaseEvidenceAndReturnSafeSearchResult() {
        KnowledgeBaseSearchTool tool = new KnowledgeBaseSearchTool(retrievalService);
        KnowledgeBaseRagRetrievalHit hit = new KnowledgeBaseRagRetrievalHit(
                1,
                10L,
                "vec-1",
                0.93d,
                7L,
                101L,
                "Ops Alpha",
                2,
                300L,
                0,
                "Alpha runbook states Redis owns session cache during deploy. "
                        + "This evidence preview keeps the useful opening sentence but should not expose the "
                        + "private appendix with tenant operations, escalation phone trees, and internal handoff notes.",
                "hash-1",
                0,
                120,
                20,
                "mock-embedding",
                "Ops Runbook / Redis",
                "paragraph",
                4,
                "page:4#block:2",
                "PAGE",
                0.92d,
                0.40d,
                0.88d,
                0.95d
        );
        KnowledgeBaseRagRetrievalResult retrieval = new KnowledgeBaseRagRetrievalResult(
                7L,
                10L,
                "cache deploy",
                4,
                2,
                List.of(101L, 102L),
                List.of(hit),
                List.of(hit.toCitation()),
                false,
                "mock",
                "kb_collection",
                "mock-embedding",
                Map.of(101L, 1, 102L, 0),
                "hybrid",
                true,
                "mock-rerank",
                true,
                3,
                2
        );
        when(retrievalService.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L, 10L, "cache deploy", 4, 2, "", true, 3
        ))).thenReturn(retrieval);

        KnowledgeBaseSearchTool.SearchResult result = tool.execute(new KnowledgeBaseSearchTool.SearchInput(
                7L,
                10L,
                "cache deploy",
                4,
                2,
                true,
                3
        ));

        ArgumentCaptor<KnowledgeBaseRagRetrievalQuery> queryCaptor =
                ArgumentCaptor.forClass(KnowledgeBaseRagRetrievalQuery.class);
        verify(retrievalService).retrieve(queryCaptor.capture());
        assertThat(queryCaptor.getValue().knowledgeBaseId()).isEqualTo(10L);
        assertThat(queryCaptor.getValue().multiQueryEnabled()).isTrue();
        assertThat(queryCaptor.getValue().maxQueryVariants()).isEqualTo(3);
        assertThat(result.noEvidence()).isFalse();
        assertThat(result.documentHitCounts()).containsEntry(101L, 1).containsEntry(102L, 0);
        assertThat(result.retrievalMode()).isEqualTo("hybrid");
        assertThat(result.rerankApplied()).isTrue();
        assertThat(result.multiQueryApplied()).isTrue();
        assertThat(result.hits().get(0).snippet()).hasSizeLessThanOrEqualTo(183);
        assertThat(result.hits().get(0).snippet()).doesNotContain("internal handoff notes");
        assertThat(result.hits().get(0).sectionPath()).isEqualTo("Ops Runbook / Redis");
        assertThat(result.hits().get(0).pageNumber()).isEqualTo(4);
        assertThat(result.hits().get(0).sourceLocator()).isEqualTo("page:4#block:2");
        assertThat(result.citations().get(0).documentTitle()).isEqualTo("Ops Alpha");
        assertThat(result.citations().get(0).sectionPath()).isEqualTo("Ops Runbook / Redis");
        assertThat(result.citations().get(0).pageNumber()).isEqualTo(4);
        assertThat(result.citations().get(0).sourceLocator()).isEqualTo("page:4#block:2");
        assertThat(result.outputSummary())
                .contains("documentCount=2")
                .contains("hitCount=1")
                .contains("multiQueryApplied=true");
    }

    @Test
    void shouldReturnNoEvidenceSummary() {
        KnowledgeBaseSearchTool tool = new KnowledgeBaseSearchTool(retrievalService);
        when(retrievalService.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L, 10L, "missing topic", null, null, "", null, null
        ))).thenReturn(new KnowledgeBaseRagRetrievalResult(
                7L,
                10L,
                "missing topic",
                3,
                1,
                List.of(101L, 102L),
                List.of(),
                List.of(),
                true,
                "mock",
                "kb_collection",
                "mock-embedding",
                Map.of(101L, 0, 102L, 0)
        ));

        KnowledgeBaseSearchTool.SearchResult result = tool.execute(new KnowledgeBaseSearchTool.SearchInput(
                7L,
                10L,
                "missing topic",
                null,
                null,
                null,
                null
        ));

        assertThat(result.noEvidence()).isTrue();
        assertThat(result.hitCount()).isZero();
        assertThat(result.documentHitCounts()).containsEntry(101L, 0).containsEntry(102L, 0);
        assertThat(result.outputSummary()).contains("noEvidence=true");
    }

    @Test
    void shouldPropagateScopeRejectionFromRetrievalService() {
        KnowledgeBaseSearchTool tool = new KnowledgeBaseSearchTool(retrievalService);
        when(retrievalService.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L, 10L, "forbidden", null, null, "", null, null
        ))).thenThrow(new BusinessException(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN));

        BusinessException ex = assertThrows(BusinessException.class, () -> tool.execute(new KnowledgeBaseSearchTool.SearchInput(
                7L,
                10L,
                "forbidden",
                null,
                null,
                null,
                null
        )));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN, ex.getErrorCode());
    }
}
