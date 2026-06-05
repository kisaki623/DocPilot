package com.docpilot.backend.ai.rag;

public record KnowledgeBaseRagQaQuery(
        Long userId,
        Long knowledgeBaseId,
        String question,
        Integer topK,
        Integer indexVersion,
        String sessionId
) {

    public KnowledgeBaseRagQaQuery {
        question = question == null ? "" : question.trim();
        sessionId = sessionId == null ? "" : sessionId.trim();
    }
}
