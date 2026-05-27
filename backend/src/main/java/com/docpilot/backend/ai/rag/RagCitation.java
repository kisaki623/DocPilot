package com.docpilot.backend.ai.rag;

import java.util.Map;

public record RagCitation(
        Long documentId,
        int chunkIndex,
        double score,
        Map<String, String> metadata
) {

    public RagCitation {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be non-negative");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
