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
import com.docpilot.backend.ai.rag.RagRetrievalProperties;
import com.docpilot.backend.ai.rag.fusion.FusedSearchHit;
import com.docpilot.backend.ai.rag.fusion.HybridRetrievalService;
import com.docpilot.backend.ai.rag.rerank.RerankProperties;
import com.docpilot.backend.ai.rag.rerank.RerankRequest;
import com.docpilot.backend.ai.rag.rerank.RerankResult;
import com.docpilot.backend.ai.rag.rerank.RerankService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseRagRetrievalServiceImplTest {

    private final KnowledgeBaseScopeGuard scopeGuard = mock(KnowledgeBaseScopeGuard.class);
    private final EmbeddingProvider embeddingProvider = mock(EmbeddingProvider.class);
    private final VectorStoreClient vectorStoreClient = mock(VectorStoreClient.class);
    private final HybridRetrievalService hybridRetrievalService = mock(HybridRetrievalService.class);
    private final RerankService rerankService = mock(RerankService.class);
    private final RagQaProperties ragQaProperties = new RagQaProperties();
    private final RagRetrievalProperties ragRetrievalProperties = new RagRetrievalProperties();
    private final RerankProperties rerankProperties = new RerankProperties();
    private final KnowledgeBaseRagRetrievalServiceImpl service = new KnowledgeBaseRagRetrievalServiceImpl(
            scopeGuard,
            embeddingProvider,
            vectorStoreClient,
            hybridRetrievalService,
            new RagEmbeddingProperties(),
            ragQaProperties,
            ragRetrievalProperties,
            rerankService,
            rerankProperties
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
    void shouldMergeMultiQueryVectorResultsWhenEnabled() {
        ragRetrievalProperties.setMultiQueryEnabled(true);
        ragRetrievalProperties.setMaxQueryVariants(4);
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of(
                doc(101L, "Cache Guide"),
                doc(102L, "Vector Guide")
        ));
        when(embeddingProvider.embed(any())).thenReturn(embedding());
        when(vectorStoreClient.search(any()))
                .thenReturn(new VectorSearchResult(List.of(
                        hit("cache", 7L, 101L, 1, "Cache invalidation policy", 0.92D)
                ), "in_memory", ""))
                .thenReturn(new VectorSearchResult(List.of(
                        hit("cache", 7L, 101L, 1, "Cache invalidation policy", 0.91D)
                ), "in_memory", ""))
                .thenReturn(new VectorSearchResult(List.of(
                        hit("cache-detail", 7L, 101L, 1, "Cache detail", 0.88D)
                ), "in_memory", ""))
                .thenReturn(new VectorSearchResult(List.of(
                        hit("vector", 7L, 102L, 1, "Vector retention policy", 0.87D)
                ), "in_memory", ""));

        KnowledgeBaseRagRetrievalResult result = service.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L,
                10L,
                "Explain cache invalidation and vector retention policy?",
                4,
                1,
                ""
        ));

        ArgumentCaptor<EmbeddingRequest> embeddingCaptor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingProvider, org.mockito.Mockito.times(4)).embed(embeddingCaptor.capture());
        verify(vectorStoreClient, org.mockito.Mockito.times(4)).search(any());
        assertThat(embeddingCaptor.getAllValues()).extracting(EmbeddingRequest::input)
                .containsExactly(
                        "Explain cache invalidation and vector retention policy?",
                        "cache invalidation and vector retention policy",
                        "cache invalidation",
                        "vector retention policy"
                );
        assertThat(embeddingCaptor.getAllValues().get(1).metadata())
                .containsEntry("queryVariantCount", "4")
                .containsEntry("queryRewriteStrategy", "cleaned_question");
        assertThat(result.multiQueryApplied()).isTrue();
        assertThat(result.queryVariantCount()).isEqualTo(4);
        assertThat(result.queryDedupeCount()).isEqualTo(1);
        assertThat(result.noEvidence()).isFalse();
        assertThat(result.hits()).hasSize(3);
        assertThat(result.documentHitCounts()).containsEntry(101L, 2).containsEntry(102L, 1);
        assertThat(result.hits()).extracting(KnowledgeBaseRagRetrievalHit::documentId)
                .contains(101L, 102L);
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
    void shouldReturnNoEvidenceWhenAllVectorHitsAreBelowSimilarityThreshold() {
        ragRetrievalProperties.setMinSimilarityThreshold(0.80D);
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of(
                doc(101L, "Payroll"),
                doc(102L, "Invoice")
        ));
        when(embeddingProvider.embed(any())).thenReturn(embedding());
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(
                hit("v1", 7L, 101L, 1, "payroll settlement unrelated nearest chunk", 0.52D),
                hit("v2", 7L, 102L, 1, "invoice approval unrelated nearest chunk", 0.48D)
        ), "in_memory", ""));

        KnowledgeBaseRagRetrievalResult result = service.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L,
                10L,
                "Which employee reimbursement matrix is defined?",
                3,
                1,
                ""
        ));

        assertThat(result.noEvidence()).isTrue();
        assertThat(result.hits()).isEmpty();
        assertThat(result.citations()).isEmpty();
        assertThat(result.documentHitCounts()).containsEntry(101L, 0).containsEntry(102L, 0);
        verify(rerankService, never()).rerank(any());
    }

    @Test
    void shouldReturnNoEvidenceForNearThresholdHardNegativeWithLowEvidenceSupport() {
        ragRetrievalProperties.setMinSimilarityThreshold(0.50D);
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of(
                doc(101L, "Chunk Metadata"),
                doc(102L, "Context Trace")
        ));
        when(embeddingProvider.embed(any())).thenReturn(embedding());
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(
                hit("v1", 7L, 101L, 1, "Chunk metadata verification checks MySQL rows before trusting RAG output.", 0.55D),
                hit("v2", 7L, 102L, 1, "Context trace records evidence counts and document hit counts.", 0.53D)
        ), "in_memory", ""));

        KnowledgeBaseRagRetrievalResult result = service.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L,
                10L,
                "Which evidence says payroll tax remittance approval is delegated to the context trace owner after chunk metadata verification?",
                3,
                1,
                ""
        ));

        assertThat(result.noEvidence()).isTrue();
        assertThat(result.hits()).isEmpty();
        assertThat(result.citations()).isEmpty();
        assertThat(result.documentHitCounts()).containsEntry(101L, 0).containsEntry(102L, 0);
    }

    @Test
    void shouldKeepNearThresholdHitsWhenEvidenceSupportsQueryTerms() {
        ragRetrievalProperties.setMinSimilarityThreshold(0.50D);
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of(doc(101L, "Payroll Approval")));
        when(embeddingProvider.embed(any())).thenReturn(embedding());
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(
                hit("v1", 7L, 101L, 1,
                        "Payroll tax remittance approval is delegated to the finance owner after chunk metadata verification.",
                        0.54D)
        ), "in_memory", ""));

        KnowledgeBaseRagRetrievalResult result = service.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L,
                10L,
                "Which evidence says payroll tax remittance approval is delegated to the finance owner after chunk metadata verification?",
                3,
                1,
                ""
        ));

        assertThat(result.noEvidence()).isFalse();
        assertThat(result.hits()).hasSize(1);
        assertThat(result.documentHitCounts()).containsEntry(101L, 1);
    }

    @Test
    void shouldReturnNoEvidenceWhenHybridFusedHitsAreBelowSimilarityThreshold() {
        ragRetrievalProperties.setHybridEnabled(true);
        ragRetrievalProperties.setMinSimilarityThreshold(0.05D);
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of(doc(101L, "Payroll")));
        when(embeddingProvider.embed(any())).thenReturn(embedding());
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(
                hit("v1", 7L, 101L, 1, "nearest vector chunk", 0.00015D)
        ), "in_memory", ""));
        when(hybridRetrievalService.hybridSearch(eq("Which reimbursement matrix is defined?"),
                eq(7L), eq(List.of(101L)), eq(1), any(), any(Integer.class)))
                .thenReturn(List.of(new FusedSearchHit(
                        901L,
                        101L,
                        7L,
                        1,
                        0,
                        "nearest keyword chunk",
                        "hash-keyword",
                        0,
                        21,
                        4,
                        "mock-model",
                        "vector-low",
                        0.00012D,
                        0.00015D,
                        0.00008D
                )));

        KnowledgeBaseRagRetrievalResult result = service.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L,
                10L,
                "Which reimbursement matrix is defined?",
                3,
                1,
                ""
        ));

        assertThat(result.retrievalMode()).isEqualTo("hybrid");
        assertThat(result.noEvidence()).isTrue();
        assertThat(result.hits()).isEmpty();
        assertThat(result.citations()).isEmpty();
        assertThat(result.documentHitCounts()).containsEntry(101L, 0);
        verify(rerankService, never()).rerank(any());
    }

    @Test
    void shouldUseVectorScoreForHybridSimilarityThreshold() {
        ragRetrievalProperties.setHybridEnabled(true);
        ragRetrievalProperties.setMinSimilarityThreshold(0.05D);
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of(doc(101L, "Doc")));
        when(embeddingProvider.embed(any())).thenReturn(embedding());
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(
                hit("v1", 7L, 101L, 1, "vector-supported chunk", 0.82D)
        ), "in_memory", ""));
        when(hybridRetrievalService.hybridSearch(eq("question"), eq(7L), eq(List.of(101L)), eq(1), any(), any(Integer.class)))
                .thenReturn(List.of(new FusedSearchHit(
                        901L,
                        101L,
                        7L,
                        1,
                        0,
                        "vector-supported chunk",
                        "hash-keyword",
                        0,
                        21,
                        4,
                        "mock-model",
                        "v1",
                        0.016D,
                        0.82D,
                        0.0D
                )));

        KnowledgeBaseRagRetrievalResult result = service.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L,
                10L,
                "question",
                3,
                1,
                ""
        ));

        assertThat(result.noEvidence()).isFalse();
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).score()).isEqualTo(0.016D);
        assertThat(result.hits().get(0).vectorScore()).isEqualTo(0.82D);
    }

    @Test
    void shouldPreserveKeywordSupportedSummaryHitsAcrossDocumentsWhenHybridThresholdIsEnabled() {
        ragRetrievalProperties.setHybridEnabled(true);
        ragRetrievalProperties.setMinSimilarityThreshold(0.50D);
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of(
                doc(101L, "Alpha Guide"),
                doc(102L, "Beta Guide")
        ));
        when(embeddingProvider.embed(any())).thenReturn(embedding());
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(
                hit("alpha-vector", 7L, 101L, 1, "PHASE2-ALPHA-GATE approved", 0.62D)
        ), "in_memory", ""));
        String query = "请总结这两个文档，必须覆盖 PHASE2-ALPHA-GATE 和 PHASE2-BETA-GATE，并列出引用来源。";
        when(hybridRetrievalService.hybridSearch(eq(query), eq(7L), eq(List.of(101L, 102L)), eq(1), any(), any(Integer.class)))
                .thenReturn(List.of(
                        new FusedSearchHit(
                                901L,
                                101L,
                                7L,
                                1,
                                0,
                                "PHASE2-ALPHA-GATE approved",
                                "hash-alpha",
                                0,
                                28,
                                4,
                                "mock-model",
                                "alpha-vector",
                                0.020D,
                                0.62D,
                                0.0D
                        ),
                        new FusedSearchHit(
                                902L,
                                102L,
                                7L,
                                1,
                                0,
                                "PHASE2-BETA-GATE approved",
                                "hash-beta",
                                0,
                                27,
                                4,
                                "mock-model",
                                null,
                                0.018D,
                                0.0D,
                                4.5D
                        )
                ));

        KnowledgeBaseRagRetrievalResult result = service.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L,
                10L,
                query,
                4,
                1,
                ""
        ));

        assertThat(result.retrievalMode()).isEqualTo("hybrid");
        assertThat(result.noEvidence()).isFalse();
        assertThat(result.hits()).hasSize(2);
        assertThat(result.documentHitCounts()).containsEntry(101L, 1).containsEntry(102L, 1);
        assertThat(result.hits()).extracting(KnowledgeBaseRagRetrievalHit::documentId)
                .containsExactly(101L, 102L);
        assertThat(result.hits().get(1).keywordScore()).isEqualTo(4.5D);
        assertThat(result.hits().get(1).vectorScore()).isEqualTo(0.0D);
    }

    @Test
    void shouldPreserveIndexVersionAndMetadataForHybridKeywordOnlyHits() {
        ragRetrievalProperties.setHybridEnabled(true);
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of(doc(101L, "Doc")));
        when(embeddingProvider.embed(any())).thenReturn(embedding());
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(), "in_memory", ""));
        when(hybridRetrievalService.hybridSearch(eq("question"), eq(7L), eq(List.of(101L)), eq(2), any(), any(Integer.class)))
                .thenReturn(List.of(new FusedSearchHit(
                        901L,
                        101L,
                        7L,
                        2,
                        3,
                        "keyword content",
                        "hash-keyword",
                        10,
                        25,
                        6,
                        "mock-model",
                        null,
                        0.42D,
                        0.0D,
                        3.2D
                )));

        KnowledgeBaseRagRetrievalResult result = service.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L,
                10L,
                "question",
                3,
                2,
                ""
        ));

        assertThat(result.retrievalMode()).isEqualTo("hybrid");
        assertThat(result.hits()).hasSize(1);
        KnowledgeBaseRagRetrievalHit hit = result.hits().get(0);
        assertThat(hit.indexVersion()).isEqualTo(2);
        assertThat(hit.chunkId()).isEqualTo(901L);
        assertThat(hit.contentHash()).isEqualTo("hash-keyword");
        assertThat(hit.fusedScore()).isEqualTo(0.42D);
        assertThat(hit.keywordScore()).isEqualTo(3.2D);
        verify(scopeGuard, org.mockito.Mockito.atLeastOnce()).requireHitInKnowledgeBaseScope(
                eq(7L),
                eq(10L),
                org.mockito.Mockito.anySet(),
                eq(2),
                any()
        );
    }

    @Test
    void shouldRerankCandidatesWhenRerankIsEnabled() {
        rerankProperties.setEnabled(true);
        when(scopeGuard.listActiveKnowledgeBaseDocuments(7L, 10L)).thenReturn(List.of(
                doc(101L, "Redis Guide"),
                doc(102L, "Qdrant Guide")
        ));
        when(embeddingProvider.embed(any())).thenReturn(embedding());
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(
                hit("v1", 7L, 101L, 1, "Redis stores cache", 0.95D),
                hit("v2", 7L, 102L, 1, "Qdrant stores vectors", 0.90D)
        ), "in_memory", ""));
        when(rerankService.rerank(any(RerankRequest.class))).thenReturn(new RerankResult(List.of(
                new RerankResult.RerankHit(1, 0.99D),
                new RerankResult.RerankHit(0, 0.60D)
        ), "mock-reranker"));

        KnowledgeBaseRagRetrievalResult result = service.retrieve(new KnowledgeBaseRagRetrievalQuery(
                7L,
                10L,
                "vectors",
                2,
                1,
                ""
        ));

        assertThat(result.rerankApplied()).isTrue();
        assertThat(result.rerankModel()).isEqualTo("mock-reranker");
        assertThat(result.hits()).extracting(KnowledgeBaseRagRetrievalHit::documentId)
                .containsExactly(102L, 101L);
        assertThat(result.hits().get(0).rerankScore()).isEqualTo(0.99D);
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
