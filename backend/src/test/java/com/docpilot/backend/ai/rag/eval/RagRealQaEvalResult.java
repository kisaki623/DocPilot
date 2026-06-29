package com.docpilot.backend.ai.rag.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RagRealQaEvalResult(
        String provider,
        String embeddingProvider,
        RagRealQaEvalMetrics metrics,
        List<CaseEvaluation> caseEvaluations,
        Map<String, KnowledgeBaseRagEvalMetrics> retrievalModeMetrics
) {

    public RagRealQaEvalResult {
        provider = provider == null || provider.isBlank() ? "unknown" : provider.trim();
        embeddingProvider = embeddingProvider == null || embeddingProvider.isBlank() ? "unknown" : embeddingProvider.trim();
        caseEvaluations = caseEvaluations == null ? List.of() : List.copyOf(caseEvaluations);
        metrics = metrics == null ? RagRealQaEvalMetrics.from(caseEvaluations) : metrics;
        retrievalModeMetrics = retrievalModeMetrics == null ? Map.of() : Map.copyOf(retrievalModeMetrics);
    }

    public List<String> failedCaseIds() {
        return caseEvaluations.stream()
                .filter(evaluation -> !evaluation.passed())
                .map(CaseEvaluation::id)
                .toList();
    }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("provider", provider);
        value.put("embeddingProvider", embeddingProvider);
        value.put("metrics", metrics.toSafeMap());
        value.put("errorAnalysis", RagRetrievalErrorAnalysis.fromRealQa(caseEvaluations).toSafeMap());
        value.put("retrievalModeMetrics", retrievalModeMetrics.entrySet().stream()
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue().toSafeMap()),
                        LinkedHashMap::putAll));
        value.put("caseSummaries", caseEvaluations.stream().map(CaseEvaluation::toSafeMap).toList());
        value.put("notes", List.of(
                "Synthetic real-QA-shaped RAG eval only",
                "No document text, query text, model input, evidence context, or model output is stored",
                "Uses MockEmbeddingProvider and InMemoryVectorStoreClient"
        ));
        return value;
    }

    public record CaseEvaluation(
            String id,
            String category,
            String retrievalMode,
            boolean expectedNoEvidence,
            int retrievedCount,
            int citationCount,
            List<Long> retrievedDocumentIds,
            List<Long> citationDocumentIds,
            List<Long> expectedDocumentIds,
            int minDocumentCoverage,
            boolean answerHit,
            boolean citationGrounded,
            boolean multiDocumentCoverageHit,
            boolean noEvidenceHit,
            boolean forbiddenAnswerHit,
            boolean scopeViolation,
            boolean rerankUpliftCandidate,
            boolean claimSupportRequired,
            int claimCount,
            int supportedClaimCount,
            int unsupportedClaimCount,
            boolean claimSupportHit,
            boolean forbiddenClaimHit,
            boolean passed,
            List<String> failureReasons
    ) {
        public CaseEvaluation {
            id = id == null ? "" : id.trim();
            category = category == null ? "" : category.trim();
            retrievalMode = retrievalMode == null ? "" : retrievalMode.trim();
            retrievedDocumentIds = retrievedDocumentIds == null ? List.of() : List.copyOf(retrievedDocumentIds);
            citationDocumentIds = citationDocumentIds == null ? List.of() : List.copyOf(citationDocumentIds);
            expectedDocumentIds = expectedDocumentIds == null ? List.of() : List.copyOf(expectedDocumentIds);
            failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
        }

        public Map<String, Object> toSafeMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("category", category);
            value.put("retrievalMode", retrievalMode);
            value.put("expectedNoEvidence", expectedNoEvidence);
            value.put("retrievedCount", retrievedCount);
            value.put("citationCount", citationCount);
            value.put("retrievedDocumentIds", retrievedDocumentIds);
            value.put("citationDocumentIds", citationDocumentIds);
            value.put("expectedDocumentIds", expectedDocumentIds);
            value.put("minDocumentCoverage", minDocumentCoverage);
            value.put("answerHit", answerHit);
            value.put("citationGrounded", citationGrounded);
            value.put("multiDocumentCoverageHit", multiDocumentCoverageHit);
            value.put("noEvidenceHit", noEvidenceHit);
            value.put("forbiddenAnswerHit", forbiddenAnswerHit);
            value.put("scopeViolation", scopeViolation);
            value.put("rerankUpliftCandidate", rerankUpliftCandidate);
            value.put("claimSupportRequired", claimSupportRequired);
            value.put("claimCount", claimCount);
            value.put("supportedClaimCount", supportedClaimCount);
            value.put("unsupportedClaimCount", unsupportedClaimCount);
            value.put("claimSupportHit", claimSupportHit);
            value.put("forbiddenClaimHit", forbiddenClaimHit);
            value.put("passed", passed);
            if (!failureReasons.isEmpty()) {
                value.put("failureReasons", failureReasons);
            }
            return value;
        }
    }
}
