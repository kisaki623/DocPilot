package com.docpilot.backend.ai.rag.keyword;

import java.util.List;

/**
 * Service for BM25-based keyword retrieval from document chunks.
 */
public interface KeywordRetrievalService {

    /**
     * Search for relevant chunks using BM25 keyword scoring.
     *
     * @param query       the search query
     * @param userId      the user ID for scope filtering
     * @param documentIds the list of document IDs to search within
     * @param indexVersion the chunk index version to search
     * @param topK        the maximum number of results to return
     * @return list of keyword search hits sorted by BM25 score (descending)
     */
    List<KeywordSearchHit> search(String query, Long userId, List<Long> documentIds, Integer indexVersion, int topK);
}
