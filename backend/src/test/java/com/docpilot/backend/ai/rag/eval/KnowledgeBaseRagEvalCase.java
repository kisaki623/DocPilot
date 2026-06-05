package com.docpilot.backend.ai.rag.eval;

import java.util.List;

public record KnowledgeBaseRagEvalCase(
        String id,
        Long userId,
        Long knowledgeBaseId,
        Integer indexVersion,
        Integer topK,
        String query,
        List<EvalDocument> documents,
        List<EvalDocument> outOfScopeDocuments,
        List<String> expectedMarkers,
        List<Long> expectedDocumentIds,
        List<Long> forbiddenDocumentIds,
        boolean expectedNoEvidence
) {

    public KnowledgeBaseRagEvalCase {
        id = id == null ? "" : id.trim();
        if (id.isBlank()) {
            throw new IllegalArgumentException("case id must not be blank");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException("knowledgeBaseId must not be null");
        }
        indexVersion = indexVersion == null ? 1 : indexVersion;
        if (indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive");
        }
        topK = topK == null ? 3 : topK;
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        query = query == null ? "" : query.trim();
        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        documents = documents == null ? List.of() : List.copyOf(documents);
        outOfScopeDocuments = outOfScopeDocuments == null ? List.of() : List.copyOf(outOfScopeDocuments);
        expectedMarkers = expectedMarkers == null ? List.of() : normalizeStrings(expectedMarkers);
        expectedDocumentIds = expectedDocumentIds == null ? List.of() : normalizeIds(expectedDocumentIds);
        forbiddenDocumentIds = forbiddenDocumentIds == null ? List.of() : normalizeIds(forbiddenDocumentIds);
    }

    public List<Long> activeDocumentIds() {
        return documents.stream()
                .map(EvalDocument::documentId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private static List<String> normalizeStrings(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    public record EvalDocument(
            Long userId,
            Long documentId,
            String title,
            Integer indexVersion,
            String text
    ) {
        public EvalDocument {
            if (documentId == null || documentId <= 0) {
                throw new IllegalArgumentException("documentId must be positive");
            }
            title = title == null ? "" : title.trim();
            text = text == null ? "" : text;
        }
    }
}
