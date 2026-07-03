package com.docpilot.backend.ai.rag;

public record KnowledgeBaseRagRetrievalQuery(
        Long userId,
        Long knowledgeBaseId,
        String query,
        Integer topK,
        Integer indexVersion,
        String embeddingModel,
        Boolean multiQueryEnabled,
        Integer maxQueryVariants
) {

    public KnowledgeBaseRagRetrievalQuery(Long userId,
                                          Long knowledgeBaseId,
                                          String query,
                                          Integer topK,
                                          Integer indexVersion,
                                          String embeddingModel) {
        this(userId, knowledgeBaseId, query, topK, indexVersion, embeddingModel, null, null);
    }

    public KnowledgeBaseRagRetrievalQuery {
        query = query == null ? "" : query.trim();
        embeddingModel = embeddingModel == null ? "" : embeddingModel.trim();
    }
}
