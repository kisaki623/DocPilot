package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.EmbeddingProvider;
import com.docpilot.backend.ai.rag.EmbeddingRequest;
import com.docpilot.backend.ai.rag.EmbeddingResult;
import com.docpilot.backend.ai.rag.EmbeddingVector;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
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
        assertThat(searchCaptor.getValue().topK()).isEqualTo(10);
        assertThat(searchCaptor.getValue().indexVersion()).isEqualTo(1);
        assertThat(result.hits()).hasSize(2);
        assertThat(result.citations()).extracting("documentTitle").containsExactly("Redis Guide", "Qdrant Guide");
        assertThat(result.noEvidence()).isFalse();
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
