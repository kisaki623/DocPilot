package com.docpilot.backend.ai.rag.rerank;

import java.util.List;

/**
 * Request for reranking documents against a query.
 */
public record RerankRequest(
        String query,
        List<String> documents,
        int topK
) {
    public RerankRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("documents must not be empty");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
    }
}
