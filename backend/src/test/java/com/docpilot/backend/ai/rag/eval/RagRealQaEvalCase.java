package com.docpilot.backend.ai.rag.eval;

import java.util.List;

public record RagRealQaEvalCase(
        String id,
        String category,
        Long userId,
        Long knowledgeBaseId,
        Integer indexVersion,
        Integer topK,
        String query,
        List<EvalDocument> documents,
        List<EvalDocument> outOfScopeDocuments,
        List<String> expectedMarkers,
        List<String> expectedAnswerMarkers,
        List<String> requiredCitationMarkers,
        List<String> forbiddenAnswerMarkers,
        List<Long> expectedDocumentIds,
        List<Long> forbiddenDocumentIds,
        Integer minCitationCount,
        Integer minDocumentCoverage,
        boolean expectedNoEvidence,
        Double minSimilarityThreshold,
        String retrievalMode,
        Boolean rerankUpliftCandidate,
        List<String> notes
) {

    public RagRealQaEvalCase {
        id = id == null ? "" : id.trim();
        if (id.isBlank()) {
            throw new IllegalArgumentException("case id must not be blank");
        }
        category = category == null || category.isBlank() ? "uncategorized" : category.trim();
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (knowledgeBaseId == null || knowledgeBaseId <= 0) {
            throw new IllegalArgumentException("knowledgeBaseId must be positive");
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
        expectedMarkers = normalizeStrings(expectedMarkers);
        expectedAnswerMarkers = expectedAnswerMarkers == null
                ? expectedMarkers
                : normalizeStrings(expectedAnswerMarkers);
        requiredCitationMarkers = requiredCitationMarkers == null
                ? expectedMarkers
                : normalizeStrings(requiredCitationMarkers);
        forbiddenAnswerMarkers = normalizeStrings(forbiddenAnswerMarkers);
        expectedDocumentIds = normalizeIds(expectedDocumentIds);
        forbiddenDocumentIds = normalizeIds(forbiddenDocumentIds);
        minCitationCount = minCitationCount == null
                ? (expectedNoEvidence ? 0 : expectedDocumentIds.size())
                : Math.max(0, minCitationCount);
        minDocumentCoverage = minDocumentCoverage == null
                ? expectedDocumentIds.size()
                : Math.max(0, minDocumentCoverage);
        minSimilarityThreshold = minSimilarityThreshold == null ? 0.0D : minSimilarityThreshold;
        if (minSimilarityThreshold < 0.0D || minSimilarityThreshold > 1.0D) {
            throw new IllegalArgumentException("minSimilarityThreshold must be between 0 and 1");
        }
        retrievalMode = retrievalMode == null || retrievalMode.isBlank() ? "both" : retrievalMode.trim();
        rerankUpliftCandidate = Boolean.TRUE.equals(rerankUpliftCandidate);
        notes = normalizeStrings(notes);
    }

    private static List<String> normalizeStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
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
