package com.docpilot.backend.ai.rag;

public record RagSearchScope(String userId, Long documentId) {

    public static final String DEFAULT_USER_ID = "system";

    public RagSearchScope {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        userId = userId.trim();
    }

    public static RagSearchScope of(String userId, Long documentId) {
        return new RagSearchScope(userId, documentId);
    }

    public static RagSearchScope system(Long documentId) {
        return new RagSearchScope(DEFAULT_USER_ID, documentId);
    }
}
