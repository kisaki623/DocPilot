package com.docpilot.backend.ai.rag;

import java.util.List;

public class RagRetrievalService {

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;

    public RagRetrievalService(EmbeddingModel embeddingModel, VectorStore vectorStore) {
        if (embeddingModel == null) {
            throw new IllegalArgumentException("embeddingModel must not be null");
        }
        if (vectorStore == null) {
            throw new IllegalArgumentException("vectorStore must not be null");
        }
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }

    public List<VectorSearchResult> retrieveForQuestion(Long documentId, String question, int topK) {
        return retrieveForQuestion(RagSearchScope.system(documentId), question, topK);
    }

    public List<VectorSearchResult> retrieveForQuestion(RagSearchScope scope, String question, int topK) {
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        if (topK <= 0) {
            return List.of();
        }
        EmbeddingVector queryVector = embeddingModel.embed(question);
        return vectorStore.searchTopK(scope, queryVector, topK);
    }
}
