package com.docpilot.backend.quality.vo;

import java.time.Instant;
import java.util.List;

public record QualityTrendPoint(
        String marker,
        String status,
        Instant updatedAt,
        int failedGateCount,
        int reviewGateCount,
        Double casePassRate,
        Integer totalTokens,
        Double estimatedCost,
        Double latencyMs,
        Double durationMs,
        List<String> failureBuckets,
        List<String> reviewBuckets
) {

    public QualityTrendPoint {
        marker = marker == null ? "" : marker.trim();
        status = status == null ? "REVIEW" : status.trim();
        failureBuckets = failureBuckets == null ? List.of() : List.copyOf(failureBuckets);
        reviewBuckets = reviewBuckets == null ? List.of() : List.copyOf(reviewBuckets);
    }
}
