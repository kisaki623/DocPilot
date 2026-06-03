package com.docpilot.backend.ai.rag;

import java.util.List;

public record RagRetrievalResult(
        Long userId,
        Long documentId,
        String query,
        int topK,
        int indexVersion,
        List<RagRetrievalHit> hits,
        List<RagEvidenceCitation> citations,
        boolean noEvidence,
        String provider,
        String collection,
        String embeddingModel
) {

    public RagRetrievalResult {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        query = query == null ? "" : query.trim();
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        if (indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive");
        }
        hits = hits == null ? List.of() : List.copyOf(hits);
        citations = citations == null ? List.of() : List.copyOf(citations);
        noEvidence = hits.isEmpty();
        provider = provider == null ? "" : provider.trim();
        collection = collection == null ? "" : collection.trim();
        embeddingModel = embeddingModel == null ? "" : embeddingModel.trim();
    }
}
