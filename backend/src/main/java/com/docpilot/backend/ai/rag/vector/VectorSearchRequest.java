package com.docpilot.backend.ai.rag.vector;

import com.docpilot.backend.ai.rag.EmbeddingVector;

public record VectorSearchRequest(
        Long userId,
        Long documentId,
        Integer indexVersion,
        EmbeddingVector queryVector,
        int topK
) {

    public VectorSearchRequest {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (indexVersion != null && indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive when provided");
        }
        if (queryVector == null) {
            throw new IllegalArgumentException("queryVector must not be null");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
    }
}
