package com.docpilot.backend.ai.rag;

import java.util.List;

public class DisabledQdrantVectorStore implements VectorStore {

    private final RagVectorStoreProperties.Qdrant properties;

    public DisabledQdrantVectorStore(RagVectorStoreProperties.Qdrant properties) {
        this.properties = properties == null ? new RagVectorStoreProperties.Qdrant() : properties;
    }

    @Override
    public void add(RagSearchScope scope, DocumentChunk chunk, EmbeddingVector vector) {
        throw disabledException();
    }

    @Override
    public List<VectorSearchResult> searchTopK(RagSearchScope scope, EmbeddingVector queryVector, int topK) {
        throw disabledException();
    }

    @Override
    public void deleteDocument(Long documentId) {
        throw disabledException();
    }

    @Override
    public void clear() {
        throw disabledException();
    }

    public String collection() {
        return properties.getCollection();
    }

    private IllegalStateException disabledException() {
        return new IllegalStateException("Qdrant vector store is disabled; skeleton does not perform HTTP requests.");
    }
}
