package com.docpilot.backend.ai.rag;

public record DocumentChunkCandidate(
        Long documentId,
        Long userId,
        int chunkIndex,
        String content,
        String contentHash,
        int startOffset,
        int endOffset,
        int tokenCount
) {

    public DocumentChunkCandidate {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be non-negative");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("offsets must be non-negative and ordered");
        }
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must be non-negative");
        }
    }
}
