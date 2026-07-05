package com.docpilot.backend.quality.vo;

import java.util.List;

public record QualityEvalCaseCatalogItem(
        String caseId,
        int caseVersion,
        String owner,
        String lastUpdated,
        String riskLevel,
        String caseLayer,
        String riskGate,
        List<String> scoringSummary,
        List<String> regressionPolicy,
        List<String> failureHistoryMarkers,
        List<String> sourceIssueIds,
        String lastVerifiedMarker,
        List<String> remediationHints,
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
        caseVersion = Math.max(caseVersion, 0);
        owner = safe(owner);
        lastUpdated = safe(lastUpdated);
        riskLevel = safe(riskLevel);
        caseLayer = safe(caseLayer);
        riskGate = safe(riskGate);
        scoringSummary = safeList(scoringSummary);
        regressionPolicy = safeList(regressionPolicy);
        failureHistoryMarkers = safeList(failureHistoryMarkers);
        sourceIssueIds = safeList(sourceIssueIds);
        lastVerifiedMarker = safe(lastVerifiedMarker);
        remediationHints = safeList(remediationHints);
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
