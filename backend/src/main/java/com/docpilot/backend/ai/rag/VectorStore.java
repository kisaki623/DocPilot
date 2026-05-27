package com.docpilot.backend.ai.rag;

import java.util.List;

public interface VectorStore {

    default void add(DocumentChunk chunk, EmbeddingVector vector) {
        add(RagSearchScope.system(chunk == null ? null : chunk.documentId()), chunk, vector);
    }

    void add(RagSearchScope scope, DocumentChunk chunk, EmbeddingVector vector);

    List<VectorSearchResult> searchTopK(RagSearchScope scope, EmbeddingVector queryVector, int topK);

    default List<VectorSearchResult> searchTopK(Long documentId, EmbeddingVector queryVector, int topK) {
        return searchTopK(RagSearchScope.system(documentId), queryVector, topK);
    }

    void deleteDocument(Long documentId);

    void clear();
}
