package com.docpilot.backend.ai.rag;

public record RagQaQuery(
        Long userId,
        Long documentId,
        String question,
        Integer topK,
        Integer indexVersion,
        String sessionId
) {

    public RagQaQuery {
        question = question == null ? "" : question.trim();
        sessionId = sessionId == null ? "" : sessionId.trim();
    }
}
