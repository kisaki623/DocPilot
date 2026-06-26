package com.docpilot.backend.ai.rag.fusion;

import com.docpilot.backend.ai.rag.RagRetrievalProperties;
import com.docpilot.backend.ai.rag.keyword.KeywordRetrievalService;
import com.docpilot.backend.ai.rag.keyword.KeywordSearchHit;
import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for hybrid retrieval combining vector and keyword search using RRF.
 */
@Service
public class HybridRetrievalService {

    private final KeywordRetrievalService keywordRetrievalService;
    private final RagRetrievalProperties retrievalProperties;

    public HybridRetrievalService(KeywordRetrievalService keywordRetrievalService,
                                  RagRetrievalProperties retrievalProperties) {
        this.keywordRetrievalService = keywordRetrievalService;
        this.retrievalProperties = retrievalProperties == null ? new RagRetrievalProperties() : retrievalProperties;
    }

    /**
     * Perform hybrid search combining vector and keyword results using RRF.
     *
     * @param query         the search query
     * @param userId        the user ID for scope filtering
     * @param documentIds   the list of document IDs to search within
     * @param vectorHits    the vector search hits (already retrieved)
     * @param candidateTopK the number of keyword candidates to retrieve
     * @return fused search hits sorted by RRF score
     */
    public List<FusedSearchHit> hybridSearch(String query,
                                              Long userId,
                                              List<Long> documentIds,
                                              Integer indexVersion,
                                              List<VectorSearchHit> vectorHits,
                                              int candidateTopK) {
        if (query == null || query.isBlank() || documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }

        // Retrieve keyword results
        List<KeywordSearchHit> keywordHits = keywordRetrievalService.search(
                query,
                userId,
                documentIds,
                indexVersion,
                candidateTopK
        );

        // Fuse using RRF
        return new ReciprocalRankFusion(retrievalProperties.getRrfK()).fuse(vectorHits, keywordHits);
    }
}
