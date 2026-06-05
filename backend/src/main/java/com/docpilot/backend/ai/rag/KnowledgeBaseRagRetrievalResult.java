package com.docpilot.backend.ai.rag;

import java.util.List;

public record KnowledgeBaseRagRetrievalResult(
        Long userId,
        Long knowledgeBaseId,
        String query,
        int topK,
        int indexVersion,
        List<Long> documentIds,
        List<KnowledgeBaseRagRetrievalHit> hits,
        List<KnowledgeBaseRagEvidenceCitation> citations,
        boolean noEvidence,
        String provider,
        String collection,
        String embeddingModel
) {

    public KnowledgeBaseRagRetrievalResult {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException("knowledgeBaseId must not be null");
        }
        query = query == null ? "" : query.trim();
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        if (indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive");
        }
        documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
        hits = hits == null ? List.of() : List.copyOf(hits);
        citations = citations == null ? List.of() : List.copyOf(citations);
        noEvidence = hits.isEmpty();
        provider = provider == null ? "" : provider.trim();
        collection = collection == null ? "" : collection.trim();
        embeddingModel = embeddingModel == null ? "" : embeddingModel.trim();
    }
}
