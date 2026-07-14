package com.docpilot.backend.ai.rag.vector;

import java.util.List;

public class DisabledVectorStoreClient implements VectorStoreClient {

    @Override
    public void upsert(List<VectorPoint> points) {
        throw disabledException();
    }

    @Override
    public VectorSearchResult search(VectorSearchRequest request) {
        throw disabledException();
    }

    @Override
    public void deleteByDocumentId(Long userId, Long documentId, Integer indexVersion) {
        throw disabledException();
    }

    private IllegalStateException disabledException() {
        return new IllegalStateException("Vector store client is disabled.");
    }
}
