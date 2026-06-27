package com.docpilot.backend.ai.rag.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public record KnowledgeBaseRagEvalResult(
        String provider,
        String embeddingProvider,
        KnowledgeBaseRagEvalMetrics metrics,
        int modelCallCount,
        int noEvidenceModelCallCount,
        List<CaseEvaluation> caseEvaluations,
        Map<String, KnowledgeBaseRagEvalMetrics> retrievalModeMetrics
) {

    public KnowledgeBaseRagEvalResult(String provider,
                                      String embeddingProvider,
                                      KnowledgeBaseRagEvalMetrics metrics,
                                      int modelCallCount,
                                      int noEvidenceModelCallCount,
                                      List<CaseEvaluation> caseEvaluations) {
        this(provider, embeddingProvider, metrics, modelCallCount, noEvidenceModelCallCount, caseEvaluations,
                Map.of("vector", metrics == null ? KnowledgeBaseRagEvalMetrics.from(caseEvaluations) : metrics));
    }

    public KnowledgeBaseRagEvalResult {
        provider = provider == null || provider.isBlank() ? "unknown" : provider.trim();
        embeddingProvider = embeddingProvider == null || embeddingProvider.isBlank() ? "unknown" : embeddingProvider.trim();
        caseEvaluations = caseEvaluations == null ? List.of() : List.copyOf(caseEvaluations);
        metrics = metrics == null ? KnowledgeBaseRagEvalMetrics.from(caseEvaluations) : metrics;
        retrievalModeMetrics = retrievalModeMetrics == null || retrievalModeMetrics.isEmpty()
                ? Map.of("vector", metrics)
                : Collections.unmodifiableMap(new LinkedHashMap<>(retrievalModeMetrics));
    }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("provider", provider);
        value.put("embeddingProvider", embeddingProvider);
        value.put("metrics", metrics.toSafeMap());
        value.put("retrievalModeMetrics", retrievalModeMetrics.entrySet().stream()
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue().toSafeMap()),
                        LinkedHashMap::putAll));
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
            boolean answerHit,
            boolean forbiddenAnswerHit,
            boolean citationCountHit,
            boolean multiDocumentCoverageRequired,
            boolean multiDocumentCoverageHit,
            boolean noEvidenceHit,
            boolean groundedAnswerHit,
            boolean noEvidenceCitationFreeHit,
            boolean scopeViolation,
            boolean modelCalledForNoEvidence,
            boolean passed,
            String errorType,
            List<String> failureReasons
    ) {
        public CaseEvaluation {
            retrievedDocumentIds = retrievedDocumentIds == null ? List.of() : List.copyOf(retrievedDocumentIds);
            citationDocumentIds = citationDocumentIds == null ? List.of() : List.copyOf(citationDocumentIds);
            expectedDocumentIds = expectedDocumentIds == null ? List.of() : List.copyOf(expectedDocumentIds);
            errorType = errorType == null ? "" : errorType.trim();
            failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
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
            value.put("answerHit", answerHit);
            value.put("forbiddenAnswerHit", forbiddenAnswerHit);
            value.put("citationCountHit", citationCountHit);
            value.put("multiDocumentCoverageRequired", multiDocumentCoverageRequired);
            value.put("multiDocumentCoverageHit", multiDocumentCoverageHit);
            value.put("noEvidenceHit", noEvidenceHit);
            value.put("groundedAnswerHit", groundedAnswerHit);
            value.put("noEvidenceCitationFreeHit", noEvidenceCitationFreeHit);
            value.put("scopeViolation", scopeViolation);
            value.put("modelCalledForNoEvidence", modelCalledForNoEvidence);
            value.put("passed", passed);
            if (!errorType.isBlank()) {
                value.put("errorType", errorType);
            }
            if (!failureReasons.isEmpty()) {
                value.put("failureReasons", failureReasons);
            }
            return value;
        }
    }
}
