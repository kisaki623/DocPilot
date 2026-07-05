package com.docpilot.backend.quality.vo;

import java.util.List;
import java.util.Map;

public record QualityEvalCaseResultDetail(
        String caseId,
        String caseType,
        String status,
        Boolean passed,
        String traceId,
        String agentRunId,
        List<String> failureBuckets,
        List<String> reviewBuckets,
        Map<String, Number> metrics,
        Map<String, Boolean> flags
) {

    public QualityEvalCaseResultDetail {
        caseId = clean(caseId);
        caseType = clean(caseType);
        status = clean(status);
        traceId = clean(traceId);
        agentRunId = clean(agentRunId);
        failureBuckets = failureBuckets == null ? List.of() : List.copyOf(failureBuckets);
        reviewBuckets = reviewBuckets == null ? List.of() : List.copyOf(reviewBuckets);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        flags = flags == null ? Map.of() : Map.copyOf(flags);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
