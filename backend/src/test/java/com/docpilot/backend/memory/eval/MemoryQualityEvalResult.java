package com.docpilot.backend.memory.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MemoryQualityEvalResult(
        MemoryQualityEvalMetrics metrics,
        List<CaseEvaluation> caseEvaluations,
        ProviderEvaluation providerEvaluation
) {

    public MemoryQualityEvalResult(MemoryQualityEvalMetrics metrics,
                                   List<CaseEvaluation> caseEvaluations) {
        this(metrics, caseEvaluations, ProviderEvaluation.ruleBasedOnly());
    }

    public MemoryQualityEvalResult {
        caseEvaluations = caseEvaluations == null ? List.of() : List.copyOf(caseEvaluations);
        metrics = metrics == null ? MemoryQualityEvalMetrics.from(caseEvaluations) : metrics;
        providerEvaluation = providerEvaluation == null ? ProviderEvaluation.ruleBasedOnly() : providerEvaluation;
    }

    public List<String> failedCaseIds() {
        return caseEvaluations.stream()
                .filter(evaluation -> !evaluation.passed())
                .map(CaseEvaluation::id)
                .toList();
    }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("metrics", metrics.toSafeMap());
        value.put("providerEvaluation", providerEvaluation.toSafeMap());
        value.put("caseSummaries", caseEvaluations.stream().map(CaseEvaluation::toSafeMap).toList());
        value.put("notes", List.of(
                "Offline memory quality eval only",
                "No raw conversation text, memory content, prompt, evidence context, token, or credential is stored",
                "Uses in-memory test doubles for mappers and existing memory/context services"
        ));
        return value;
    }

    public record CaseEvaluation(
            String id,
            String category,
            int extractedSuggestionCount,
            List<String> suggestionTypes,
            List<Long> selectedMemoryIds,
            Map<String, Integer> contextSourceCounts,
            boolean suggestionTypesHit,
            boolean activeMemorySelectionHit,
            boolean sensitiveRejectionExpected,
            boolean sensitiveRejected,
            boolean suggestionSafetyHit,
            boolean ragEvidenceIsolationHit,
            boolean traceCountsHit,
            boolean passed,
            List<String> failureReasons,
            String extractionProvider,
            boolean providerBacked
    ) {
        public CaseEvaluation(String id,
                              String category,
                              int extractedSuggestionCount,
                              List<String> suggestionTypes,
                              List<Long> selectedMemoryIds,
                              Map<String, Integer> contextSourceCounts,
                              boolean suggestionTypesHit,
                              boolean activeMemorySelectionHit,
                              boolean sensitiveRejectionExpected,
                              boolean sensitiveRejected,
                              boolean suggestionSafetyHit,
                              boolean ragEvidenceIsolationHit,
                              boolean traceCountsHit,
                              boolean passed,
                              List<String> failureReasons) {
            this(id,
                    category,
                    extractedSuggestionCount,
                    suggestionTypes,
                    selectedMemoryIds,
                    contextSourceCounts,
                    suggestionTypesHit,
                    activeMemorySelectionHit,
                    sensitiveRejectionExpected,
                    sensitiveRejected,
                    suggestionSafetyHit,
                    ragEvidenceIsolationHit,
                    traceCountsHit,
                    passed,
                    failureReasons,
                    "rule_based",
                    false);
        }

        public CaseEvaluation {
            id = id == null ? "" : id.trim();
            category = category == null ? "" : category.trim();
            suggestionTypes = suggestionTypes == null ? List.of() : List.copyOf(suggestionTypes);
            selectedMemoryIds = selectedMemoryIds == null ? List.of() : List.copyOf(selectedMemoryIds);
            contextSourceCounts = contextSourceCounts == null ? Map.of() : Map.copyOf(contextSourceCounts);
            failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
            extractionProvider = extractionProvider == null || extractionProvider.isBlank()
                    ? "rule_based"
                    : extractionProvider.trim();
        }

        public Map<String, Object> toSafeMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("category", category);
            value.put("extractedSuggestionCount", extractedSuggestionCount);
            value.put("suggestionTypes", suggestionTypes);
            value.put("selectedMemoryIds", selectedMemoryIds);
            value.put("contextSourceCounts", contextSourceCounts);
            value.put("suggestionTypesHit", suggestionTypesHit);
            value.put("activeMemorySelectionHit", activeMemorySelectionHit);
            value.put("sensitiveRejectionExpected", sensitiveRejectionExpected);
            value.put("sensitiveRejected", sensitiveRejected);
            value.put("suggestionSafetyHit", suggestionSafetyHit);
            value.put("ragEvidenceIsolationHit", ragEvidenceIsolationHit);
            value.put("traceCountsHit", traceCountsHit);
            value.put("extractionProvider", extractionProvider);
            value.put("providerBacked", providerBacked);
            value.put("passed", passed);
            if (!failureReasons.isEmpty()) {
                value.put("failureReasons", failureReasons);
            }
            return value;
        }
    }

    public record ProviderEvaluation(
            String extractionProvider,
            String status,
            boolean realProviderConfigured,
            int modelCallCount,
            boolean rawProviderOutputStored,
            String boundary
    ) {

        public static ProviderEvaluation ruleBasedOnly() {
            return new ProviderEvaluation(
                    "rule_based",
                    "not_configured",
                    false,
                    0,
                    false,
                    "Real provider memory extraction is not evaluated by the offline runner"
            );
        }

        public ProviderEvaluation {
            extractionProvider = extractionProvider == null || extractionProvider.isBlank()
                    ? "rule_based"
                    : extractionProvider.trim();
            status = status == null || status.isBlank() ? "not_configured" : status.trim();
            boundary = boundary == null ? "" : boundary.trim();
            modelCallCount = Math.max(0, modelCallCount);
        }

        public Map<String, Object> toSafeMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("extractionProvider", extractionProvider);
            value.put("status", status);
            value.put("realProviderConfigured", realProviderConfigured);
            value.put("modelCallCount", modelCallCount);
            value.put("rawProviderOutputStored", rawProviderOutputStored);
            value.put("boundary", boundary);
            return value;
        }
    }
}
