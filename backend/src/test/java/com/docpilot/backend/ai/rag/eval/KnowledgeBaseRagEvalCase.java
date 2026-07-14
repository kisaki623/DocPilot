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
        List<String> expectedAnswerMarkers,
        List<String> forbiddenAnswerMarkers,
        List<Long> expectedDocumentIds,
        List<Long> forbiddenDocumentIds,
        Integer minCitationCount,
        Boolean requiresMultiDocumentCoverage,
        boolean expectedNoEvidence,
        Double minSimilarityThreshold
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
        expectedAnswerMarkers = expectedAnswerMarkers == null
                ? expectedMarkers
                : normalizeStrings(expectedAnswerMarkers);
        forbiddenAnswerMarkers = forbiddenAnswerMarkers == null ? List.of() : normalizeStrings(forbiddenAnswerMarkers);
        expectedDocumentIds = expectedDocumentIds == null ? List.of() : normalizeIds(expectedDocumentIds);
        forbiddenDocumentIds = forbiddenDocumentIds == null ? List.of() : normalizeIds(forbiddenDocumentIds);
        minCitationCount = minCitationCount == null
                ? (expectedNoEvidence ? 0 : expectedDocumentIds.size())
                : Math.max(0, minCitationCount);
        requiresMultiDocumentCoverage = requiresMultiDocumentCoverage == null
                ? expectedDocumentIds.size() > 1
                : requiresMultiDocumentCoverage;
        minSimilarityThreshold = minSimilarityThreshold == null ? 0.0D : minSimilarityThreshold;
        if (minSimilarityThreshold < 0.0D || minSimilarityThreshold > 1.0D) {
            throw new IllegalArgumentException("minSimilarityThreshold must be between 0 and 1");
        }
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
