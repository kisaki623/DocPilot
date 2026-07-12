package com.docpilot.backend.quality.vo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record QualityDomainTrendSummary(
        String domain,
        String label,
        int runCount,
        String latestMarker,
        String latestStatus,
        Instant latestUpdatedAt,
        Double averagePassRate,
        Map<String, Number> latestMetrics,
        Map<String, Boolean> latestFlags,
        Map<String, Number> averageMetrics,
        Map<String, Integer> failureBucketCounts,
        Map<String, Integer> reviewBucketCounts,
        List<QualityDomainTrendPoint> points
) {

    public QualityDomainTrendSummary {
        domain = domain == null ? "" : domain.trim();
        label = label == null ? "" : label.trim();
        latestMarker = latestMarker == null ? "" : latestMarker.trim();
        latestStatus = latestStatus == null ? "REVIEW" : latestStatus.trim();
        latestMetrics = latestMetrics == null ? Map.of() : Map.copyOf(latestMetrics);
        latestFlags = latestFlags == null ? Map.of() : Map.copyOf(latestFlags);
        averageMetrics = averageMetrics == null ? Map.of() : Map.copyOf(averageMetrics);
        failureBucketCounts = failureBucketCounts == null ? Map.of() : Map.copyOf(failureBucketCounts);
        reviewBucketCounts = reviewBucketCounts == null ? Map.of() : Map.copyOf(reviewBucketCounts);
        points = points == null ? List.of() : List.copyOf(points);
    }
}
