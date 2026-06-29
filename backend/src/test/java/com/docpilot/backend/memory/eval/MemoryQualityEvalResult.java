package com.docpilot.backend.memory.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MemoryQualityEvalResult(
        MemoryQualityEvalMetrics metrics,
        List<CaseEvaluation> caseEvaluations
) {

    public MemoryQualityEvalResult {
        caseEvaluations = caseEvaluations == null ? List.of() : List.copyOf(caseEvaluations);
        metrics = metrics == null ? MemoryQualityEvalMetrics.from(caseEvaluations) : metrics;
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
            List<String> failureReasons
    ) {
        public CaseEvaluation {
            id = id == null ? "" : id.trim();
            category = category == null ? "" : category.trim();
            suggestionTypes = suggestionTypes == null ? List.of() : List.copyOf(suggestionTypes);
            selectedMemoryIds = selectedMemoryIds == null ? List.of() : List.copyOf(selectedMemoryIds);
            contextSourceCounts = contextSourceCounts == null ? Map.of() : Map.copyOf(contextSourceCounts);
            failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
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
            value.put("passed", passed);
            if (!failureReasons.isEmpty()) {
                value.put("failureReasons", failureReasons);
            }
            return value;
        }
    }
}
