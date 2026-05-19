package com.docpilot.backend.ai.rag;

import java.util.Map;

public record DocumentChunk(
        Long documentId,
        int chunkIndex,
        String text,
        Map<String, String> metadata
) {

    public DocumentChunk {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be non-negative");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
