package com.docpilot.backend.ai.rag.vector;

import java.util.Map;

public record VectorSearchHit(
        String id,
        double score,
        Long userId,
        Long documentId,
        Integer indexVersion,
        Integer chunkIndex,
        String content,
        String contentHash,
        Map<String, Object> payload
) {

    public VectorSearchHit {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (indexVersion == null || indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive");
        }
        if (chunkIndex == null || chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (contentHash == null) {
            throw new IllegalArgumentException("contentHash must not be null");
        }
        id = id.trim();
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
