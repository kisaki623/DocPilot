package com.docpilot.backend.ai.rag;

public record VectorSearchResult(
        DocumentChunk chunk,
        double score
) {

    public VectorSearchResult {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk must not be null");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
    }
}
