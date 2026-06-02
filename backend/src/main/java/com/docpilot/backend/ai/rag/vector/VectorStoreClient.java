package com.docpilot.backend.ai.rag.vector;

import java.util.List;

public interface VectorStoreClient {

    void upsert(List<VectorPoint> points);

    VectorSearchResult search(VectorSearchRequest request);

    void deleteByDocumentId(Long userId, Long documentId, Integer indexVersion);
}
