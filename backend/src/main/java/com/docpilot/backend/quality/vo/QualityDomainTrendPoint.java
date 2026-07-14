package com.docpilot.backend.quality.vo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record QualityDomainTrendPoint(
        String marker,
        String status,
        Instant updatedAt,
        Double passRate,
        Map<String, Number> metrics,
        Map<String, Boolean> flags,
        List<String> failureBuckets,
        List<String> reviewBuckets
) {

    public QualityDomainTrendPoint {
        marker = marker == null ? "" : marker.trim();
        status = status == null ? "REVIEW" : status.trim();
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        flags = flags == null ? Map.of() : Map.copyOf(flags);
        failureBuckets = failureBuckets == null ? List.of() : List.copyOf(failureBuckets);
        reviewBuckets = reviewBuckets == null ? List.of() : List.copyOf(reviewBuckets);
    }
}
