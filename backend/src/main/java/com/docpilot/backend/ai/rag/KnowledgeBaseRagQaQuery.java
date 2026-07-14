package com.docpilot.backend.ai.rag;

public record KnowledgeBaseRagQaQuery(
        Long userId,
        Long knowledgeBaseId,
        String question,
        Integer topK,
        Integer indexVersion,
        String sessionId,
        Boolean multiQueryEnabled,
        Integer maxQueryVariants
) {

    public KnowledgeBaseRagQaQuery(Long userId,
                                   Long knowledgeBaseId,
                                   String question,
                                   Integer topK,
                                   Integer indexVersion,
                                   String sessionId) {
        this(userId, knowledgeBaseId, question, topK, indexVersion, sessionId, null, null);
    }

    public KnowledgeBaseRagQaQuery {
        question = question == null ? "" : question.trim();
        sessionId = sessionId == null ? "" : sessionId.trim();
    }
}
