package com.docpilot.backend.ai.rag;

public record RagIndexingResult(
        RagIndexingStatus status,
        Long documentId,
        Long userId,
        Integer indexVersion,
        int chunkCount,
        int vectorCount,
        String message
) {

    public RagIndexingResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        message = message == null ? "" : message.trim();
    }

    public boolean success() {
        return RagIndexingStatus.SUCCESS.equals(status);
    }
}
