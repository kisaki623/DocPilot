package com.docpilot.backend.quality.vo;

import java.time.Instant;
import java.util.List;

public record QualityRunSummary(
        String marker,
        String source,
        String artifactName,
        String status,
        Instant updatedAt,
        int gateCount,
        int failedGateCount,
        int reviewGateCount,
        List<String> failureBuckets,
        List<String> reviewBuckets,
        QualityTokenUsageSummary tokenUsage,
        boolean artifactMissing,
        boolean artifactParseFailed
) {

    public QualityRunSummary {
        failureBuckets = failureBuckets == null ? List.of() : List.copyOf(failureBuckets);
        reviewBuckets = reviewBuckets == null ? List.of() : List.copyOf(reviewBuckets);
        tokenUsage = tokenUsage == null ? QualityTokenUsageSummary.empty() : tokenUsage;
    }
}
