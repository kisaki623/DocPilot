package com.docpilot.backend.ai.rag;

import java.util.List;

public interface VectorStore {

    void add(DocumentChunk chunk, EmbeddingVector vector);

    List<VectorSearchResult> searchTopK(Long documentId, EmbeddingVector queryVector, int topK);

    void clear();
}
