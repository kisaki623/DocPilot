package com.docpilot.backend.ai.rag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        String embeddingModel,
        Map<Long, Integer> documentHitCounts,
        String retrievalMode,
        Boolean rerankApplied,
        String rerankModel
) {

    public KnowledgeBaseRagRetrievalResult(Long userId,
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
                                           String embeddingModel,
                                           Map<Long, Integer> documentHitCounts) {
        this(userId, knowledgeBaseId, query, topK, indexVersion, documentIds, hits, citations,
                noEvidence, provider, collection, embeddingModel, documentHitCounts, "vector", false, "");
    }

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
        documentHitCounts = normalizeDocumentHitCounts(documentIds, hits, documentHitCounts);
        retrievalMode = retrievalMode == null ? "vector" : retrievalMode.trim();
        rerankApplied = Boolean.TRUE.equals(rerankApplied);
        rerankModel = rerankModel == null ? "" : rerankModel.trim();
    }

    private static Map<Long, Integer> normalizeDocumentHitCounts(List<Long> documentIds,
                                                                 List<KnowledgeBaseRagRetrievalHit> hits,
                                                                 Map<Long, Integer> providedCounts) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Long documentId : documentIds) {
            if (documentId != null) {
                counts.put(documentId, 0);
            }
        }
        if (providedCounts != null && !providedCounts.isEmpty()) {
            providedCounts.forEach((documentId, count) -> {
                if (documentId != null) {
                    counts.put(documentId, Math.max(0, count == null ? 0 : count));
                }
            });
            return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
        }
        for (KnowledgeBaseRagRetrievalHit hit : hits) {
            if (hit.documentId() != null) {
                counts.merge(hit.documentId(), 1, Integer::sum);
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }
}
