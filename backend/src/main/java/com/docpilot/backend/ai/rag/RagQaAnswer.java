package com.docpilot.backend.ai.rag;

public record RagQaAnswer(
        Long userId,
        Long documentId,
        String question,
        String answer,
        String sessionId,
        RagRetrievalResult retrieval,
        boolean noEvidence,
        boolean fallbackUsed,
        String fallbackReason
) {

    public RagQaAnswer {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        question = question == null ? "" : question.trim();
        answer = answer == null ? "" : answer.trim();
        sessionId = sessionId == null ? "" : sessionId.trim();
        fallbackReason = fallbackReason == null ? "" : fallbackReason.trim();
    }
}
