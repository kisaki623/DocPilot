package com.docpilot.backend.quality.vo;

import java.util.List;

public record QualityEvalCaseCatalogItem(
        String caseId,
        String caseType,
        List<String> tags,
        List<String> expectedEvidence,
        List<String> expectedTools,
        List<String> scoringRules,
        String latestStatus,
        String latestRunMarker,
        String latestTraceId,
        String latestAgentRunId,
        List<String> latestFailureBuckets,
        List<String> latestReviewBuckets
) {

    public QualityEvalCaseCatalogItem {
        caseId = safe(caseId);
        caseType = safe(caseType);
        tags = safeList(tags);
        expectedEvidence = safeList(expectedEvidence);
        expectedTools = safeList(expectedTools);
        scoringRules = safeList(scoringRules);
        latestStatus = safe(latestStatus);
        latestRunMarker = safe(latestRunMarker);
        latestTraceId = safe(latestTraceId);
        latestAgentRunId = safe(latestAgentRunId);
        latestFailureBuckets = safeList(latestFailureBuckets);
        latestReviewBuckets = safeList(latestReviewBuckets);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> safeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(QualityEvalCaseCatalogItem::safe)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
