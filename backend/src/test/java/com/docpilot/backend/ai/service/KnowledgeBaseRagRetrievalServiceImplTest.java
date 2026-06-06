package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.EmbeddingProvider;
import com.docpilot.backend.ai.rag.EmbeddingRequest;
import com.docpilot.backend.ai.rag.EmbeddingResult;
import com.docpilot.backend.ai.rag.EmbeddingVector;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.rag.RagEmbeddingProperties;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import com.docpilot.backend.ai.rag.vector.VectorSearchRequest;
import com.docpilot.backend.ai.rag.vector.VectorSearchResult;
import com.docpilot.backend.ai.rag.vector.VectorStoreClient;
import com.docpilot.backend.ai.service.impl.KnowledgeBaseRagRetrievalServiceImpl;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.knowledge.service.KnowledgeBaseScopeGuard;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseRagRetrievalServiceImplTest {

    private final KnowledgeBaseScopeGuard scopeGuard = mock(KnowledgeBaseScopeGuard.class);
    private final EmbeddingProvider embeddingProvider = mock(EmbeddingProvider.class);
    private final VectorStoreClient vectorStoreClient = mock(VectorStoreClient.class);
    private final RagQaProperties ragQaProperties = new RagQaProperties();
    private final KnowledgeBaseRagRetrievalServiceImpl service = new KnowledgeBaseRagRetrievalServiceImpl(
            scopeGuard,
            embeddingProvider,
            vectorStoreClient,
            new RagEmbeddingProperties(),
            ragQaProperties
    );

    @Test
    void shouldRetrieveAcrossKnowledgeBaseDocumentsWithDocumentIdsFilter() {
        ragQaProperties.setTopK(4);
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of(
                doc(101L, "Redis Guide"),
                doc(102L, "Qdrant Guide")
        ));
        when(embeddingProvider.embed(any())).thenReturn(embedding());
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(
                hit("v1", 7L, 101L, 1, "Redis stores cache", 0.95D),
                hit("v2", 7L, 102L, 1, "Qdrant stores vectors", 0.90D)
        ), "in_memory", ""));

        KnowledgeBaseRagRetrievalResult result = service.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L,
                10L,
                "cache vectors",
                50,
                null,
                ""
        ));

        ArgumentCaptor<VectorSearchRequest> searchCaptor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        verify(vectorStoreClient).search(searchCaptor.capture());
        assertThat(searchCaptor.getValue().userId()).isEqualTo(7L);
        assertThat(searchCaptor.getValue().documentIds()).containsExactly(101L, 102L);
        assertThat(searchCaptor.getValue().topK()).isEqualTo(40);
        assertThat(searchCaptor.getValue().indexVersion()).isEqualTo(1);
        assertThat(result.topK()).isEqualTo(10);
        assertThat(result.hits()).hasSize(2);
        assertThat(result.citations()).extracting("documentTitle").containsExactly("Redis Guide", "Qdrant Guide");
        assertThat(result.documentHitCounts()).containsEntry(101L, 1).containsEntry(102L, 1);
        assertThat(result.noEvidence()).isFalse();
    }

    @Test
    void shouldPreferPerDocumentCoverageForSummaryQuestions() {
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of(
                doc(101L, "Harness"),
                doc(102L, "MCP"),
                doc(103L, "Skill"),
                doc(104L, "RAG")
        ));
        when(embeddingProvider.embed(any())).thenReturn(embedding());
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(
                hit("rag-1", 7L, 104L, 1, "RAG top one", 0.99D),
                hit("rag-2", 7L, 104L, 1, "RAG top two", 0.98D),
                hit("rag-3", 7L, 104L, 1, "RAG top three", 0.97D),
                hit("rag-4", 7L, 104L, 1, "RAG top four", 0.96D),
                hit("harness-1", 7L, 101L, 1, "Harness content", 0.80D),
                hit("mcp-1", 7L, 102L, 1, "MCP content", 0.79D),
                hit("skill-1", 7L, 103L, 1, "Skill content", 0.78D),
                hit("harness-2", 7L, 101L, 1, "Harness second", 0.77D)
        ), "in_memory", ""));

        KnowledgeBaseRagRetrievalResult result = service.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L,
                10L,
                "请你阅读资料集，帮我总结一下资料及里面文档的内容",
                6,
                1,
                ""
        ));

        ArgumentCaptor<VectorSearchRequest> searchCaptor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        verify(vectorStoreClient).search(searchCaptor.capture());
        assertThat(searchCaptor.getValue().topK()).isEqualTo(24);
        assertThat(result.hits()).hasSize(6);
        assertThat(result.documentHitCounts())
                .containsEntry(101L, 2)
                .containsEntry(102L, 1)
                .containsEntry(103L, 1)
                .containsEntry(104L, 2);
        assertThat(result.hits()).extracting(KnowledgeBaseRagRetrievalHit::documentId)
                .contains(101L, 102L, 103L, 104L);
    }

    @Test
    void shouldReturnNoEvidenceForEmptyKnowledgeBaseWithoutEmbedding() {
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of());

        KnowledgeBaseRagRetrievalResult result = service.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L,
                10L,
                "question",
                3,
                1,
                ""
        ));

        assertThat(result.noEvidence()).isTrue();
        verify(embeddingProvider, never()).embed(any());
        verify(vectorStoreClient, never()).search(any());
    }

    @Test
    void shouldRejectHitOutsideKnowledgeBaseScope() {
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of(doc(101L, "Doc")));
        when(embeddingProvider.embed(any())).thenReturn(embedding());
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(
                hit("v1", 7L, 102L, 1, "outside", 0.9D)
        ), "in_memory", ""));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN))
                .when(scopeGuard)
                .requireHitInKnowledgeBaseScope(
                        org.mockito.Mockito.eq(7L),
                        org.mockito.Mockito.eq(10L),
                        org.mockito.Mockito.anySet(),
                        org.mockito.Mockito.eq(1),
                        org.mockito.Mockito.any()
                );

        BusinessException ex = assertThrows(BusinessException.class, () -> service.retrieve(
                new KnowledgeBaseRagRetrievalQuery(7L, 10L, "question", 3, 1, "")
        ));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void shouldRejectBlankQuery() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.retrieve(
                new KnowledgeBaseRagRetrievalQuery(7L, 10L, " ", 3, 1, "")
        ));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    private KnowledgeBaseDocumentResponse doc(Long documentId, String title) {
        KnowledgeBaseDocumentResponse response = new KnowledgeBaseDocumentResponse();
        response.setDocumentId(documentId);
        response.setDocumentTitle(title);
        return response;
    }

    private EmbeddingResult embedding() {
        EmbeddingVector vector = new EmbeddingVector(List.of(0.1D, 0.2D, 0.3D));
        return new EmbeddingResult(vector, "mock", "mock-model", vector.dimension(), Map.of());
    }

    private VectorSearchHit hit(String id, Long userId, Long documentId, Integer indexVersion, String content, double score) {
        return new VectorSearchHit(
                id,
                score,
                userId,
                documentId,
                indexVersion,
                0,
                content,
                "hash-" + id,
                Map.of(
                        "chunkId", 900L,
                        "startOffset", 0,
                        "endOffset", content.length(),
                        "tokenCount", 4,
                        "embeddingModel", "mock-model"
                )
        );
    }
}
