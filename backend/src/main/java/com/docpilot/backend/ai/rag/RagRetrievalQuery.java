package com.docpilot.backend.ai.rag;

public record RagRetrievalQuery(
        Long userId,
        Long documentId,
        String query,
        Integer topK,
        Integer indexVersion,
        String embeddingModel
) {

    public RagRetrievalQuery {
        query = query == null ? "" : query.trim();
        embeddingModel = embeddingModel == null ? "" : embeddingModel.trim();
    }
}
