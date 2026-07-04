package com.docpilot.backend.quality.vo;

import java.util.List;
import java.util.Map;

public record QualityGateSummary(
        String name,
        String status,
        Boolean passed,
        Map<String, Number> metrics,
        Map<String, Boolean> flags,
        List<String> failureBuckets,
        List<String> reviewBuckets
) {

    public QualityGateSummary {
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        flags = flags == null ? Map.of() : Map.copyOf(flags);
        failureBuckets = failureBuckets == null ? List.of() : List.copyOf(failureBuckets);
        reviewBuckets = reviewBuckets == null ? List.of() : List.copyOf(reviewBuckets);
    }
}
