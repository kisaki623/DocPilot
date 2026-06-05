package com.docpilot.backend.ai.rag.vector;

import com.docpilot.backend.ai.rag.EmbeddingVector;

import java.util.List;

public record VectorSearchRequest(
        Long userId,
        Long documentId,
        List<Long> documentIds,
        Integer indexVersion,
        EmbeddingVector queryVector,
        int topK
) {

    public VectorSearchRequest {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        documentIds = normalizeDocumentIds(documentIds);
        if (documentIds.isEmpty() && documentId == null) {
            throw new IllegalArgumentException("documentId or documentIds must not be null");
        }
        if (indexVersion != null && indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive when provided");
        }
        if (queryVector == null) {
            throw new IllegalArgumentException("queryVector must not be null");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
    }

    public VectorSearchRequest(Long userId,
                               Long documentId,
                               Integer indexVersion,
                               EmbeddingVector queryVector,
                               int topK) {
        this(userId, documentId, List.of(), indexVersion, queryVector, topK);
    }

    public static VectorSearchRequest forDocuments(Long userId,
                                                   List<Long> documentIds,
                                                   Integer indexVersion,
                                                   EmbeddingVector queryVector,
                                                   int topK) {
        return new VectorSearchRequest(userId, null, documentIds, indexVersion, queryVector, topK);
    }

    public boolean hasDocumentIdsFilter() {
        return documentIds != null && !documentIds.isEmpty();
    }

    public List<Long> effectiveDocumentIds() {
        if (hasDocumentIdsFilter()) {
            return documentIds;
        }
        return List.of(documentId);
    }

    private static List<Long> normalizeDocumentIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        return documentIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }
}
