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
        boolean artifactParseFailed,
        String environment,
        String dataSource,
        Instant importedAt
) {

    public QualityRunSummary {
        failureBuckets = failureBuckets == null ? List.of() : List.copyOf(failureBuckets);
        reviewBuckets = reviewBuckets == null ? List.of() : List.copyOf(reviewBuckets);
        tokenUsage = tokenUsage == null ? QualityTokenUsageSummary.empty() : tokenUsage;
        environment = environment == null || environment.isBlank() ? "" : environment.trim();
        dataSource = dataSource == null || dataSource.isBlank() ? "" : dataSource.trim();
    }

    public QualityRunSummary(
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
            boolean artifactParseFailed) {
        this(marker, source, artifactName, status, updatedAt, gateCount, failedGateCount, reviewGateCount,
                failureBuckets, reviewBuckets, tokenUsage, artifactMissing, artifactParseFailed,
                "", "", null);
    }
}
