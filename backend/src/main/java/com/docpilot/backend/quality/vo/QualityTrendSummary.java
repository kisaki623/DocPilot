package com.docpilot.backend.quality.vo;

import java.util.List;
import java.util.Map;

public record QualityTrendSummary(
        int limit,
        int runCount,
        Map<String, Integer> statusCounts,
        Map<String, Integer> failureBucketCounts,
        Map<String, Integer> reviewBucketCounts,
        Double averageCasePassRate,
        Integer totalTokens,
        Double estimatedCost,
        Double averageLatencyMs,
        Double averageDurationMs,
        List<QualityRepeatedCaseSummary> repeatedCases,
        List<QualityTrendPoint> points
) {

    public QualityTrendSummary {
        statusCounts = statusCounts == null ? Map.of() : Map.copyOf(statusCounts);
        failureBucketCounts = failureBucketCounts == null ? Map.of() : Map.copyOf(failureBucketCounts);
        reviewBucketCounts = reviewBucketCounts == null ? Map.of() : Map.copyOf(reviewBucketCounts);
        repeatedCases = repeatedCases == null ? List.of() : List.copyOf(repeatedCases);
        points = points == null ? List.of() : List.copyOf(points);
    }
}
