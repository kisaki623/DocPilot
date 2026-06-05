package com.docpilot.backend.ai.rag.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record KnowledgeBaseRagEvalResult(
        String provider,
        String embeddingProvider,
        KnowledgeBaseRagEvalMetrics metrics,
        int modelCallCount,
        int noEvidenceModelCallCount,
        List<CaseEvaluation> caseEvaluations
) {

    public KnowledgeBaseRagEvalResult {
        provider = provider == null || provider.isBlank() ? "unknown" : provider.trim();
        embeddingProvider = embeddingProvider == null || embeddingProvider.isBlank() ? "unknown" : embeddingProvider.trim();
        caseEvaluations = caseEvaluations == null ? List.of() : List.copyOf(caseEvaluations);
        metrics = metrics == null ? KnowledgeBaseRagEvalMetrics.from(caseEvaluations) : metrics;
    }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("provider", provider);
        value.put("embeddingProvider", embeddingProvider);
        value.put("metrics", metrics.toSafeMap());
        value.put("modelCallCount", modelCallCount);
        value.put("noEvidenceModelCallCount", noEvidenceModelCallCount);
        value.put("caseSummaries", caseEvaluations.stream().map(CaseEvaluation::toSafeMap).toList());
        value.put("notes", List.of(
                "Synthetic knowledge-base RAG eval cases only",
                "No document text, model input, evidence context, or model output is stored",
                "Uses MockEmbeddingProvider and InMemoryVectorStoreClient"
        ));
        return value;
    }

    public List<String> failedCaseIds() {
        return caseEvaluations.stream()
                .filter(evaluation -> !evaluation.passed())
                .map(CaseEvaluation::id)
                .toList();
    }

    public record CaseEvaluation(
            String id,
            boolean expectedNoEvidence,
            int retrievedCount,
            int citationCount,
            List<Long> retrievedDocumentIds,
            List<Long> citationDocumentIds,
            List<Long> expectedDocumentIds,
            boolean hit,
            boolean documentHit,
            boolean citationHit,
            boolean noEvidenceHit,
            boolean scopeViolation,
            boolean modelCalledForNoEvidence,
            boolean passed,
            String errorType
    ) {
        public CaseEvaluation {
            retrievedDocumentIds = retrievedDocumentIds == null ? List.of() : List.copyOf(retrievedDocumentIds);
            citationDocumentIds = citationDocumentIds == null ? List.of() : List.copyOf(citationDocumentIds);
            expectedDocumentIds = expectedDocumentIds == null ? List.of() : List.copyOf(expectedDocumentIds);
            errorType = errorType == null ? "" : errorType.trim();
        }

        public Map<String, Object> toSafeMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("expectedNoEvidence", expectedNoEvidence);
            value.put("retrievedCount", retrievedCount);
            value.put("citationCount", citationCount);
            value.put("retrievedDocumentIds", retrievedDocumentIds);
            value.put("citationDocumentIds", citationDocumentIds);
            value.put("expectedDocumentIds", expectedDocumentIds);
            value.put("hit", hit);
            value.put("documentHit", documentHit);
            value.put("citationHit", citationHit);
            value.put("noEvidenceHit", noEvidenceHit);
            value.put("scopeViolation", scopeViolation);
            value.put("modelCalledForNoEvidence", modelCalledForNoEvidence);
            value.put("passed", passed);
            if (!errorType.isBlank()) {
                value.put("errorType", errorType);
            }
            return value;
        }
    }
}
