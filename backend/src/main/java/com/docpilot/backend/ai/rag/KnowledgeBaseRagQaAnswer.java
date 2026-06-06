package com.docpilot.backend.ai.rag;

public record KnowledgeBaseRagQaAnswer(
        Long userId,
        Long knowledgeBaseId,
        String question,
        String answer,
        String sessionId,
        KnowledgeBaseRagRetrievalResult retrieval,
        boolean noEvidence,
        boolean fallbackUsed,
        String fallbackReason,
        String answerProvider,
        String answerModel,
        int modelCallCount
) {

    public KnowledgeBaseRagQaAnswer {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException("knowledgeBaseId must not be null");
        }
        question = question == null ? "" : question.trim();
        answer = answer == null ? "" : answer.trim();
        sessionId = sessionId == null ? "" : sessionId.trim();
        fallbackReason = fallbackReason == null ? "" : fallbackReason.trim();
        answerProvider = answerProvider == null ? "" : answerProvider.trim();
        answerModel = answerModel == null ? "" : answerModel.trim();
        modelCallCount = Math.max(0, modelCallCount);
    }
}
