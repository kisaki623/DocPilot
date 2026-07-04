package com.docpilot.backend.quality.vo;

import java.util.List;

public record QualityEvalCaseResultDetail(
        String caseId,
        String caseType,
        String status,
        Boolean passed,
        String traceId,
        String agentRunId,
        List<String> failureBuckets,
        List<String> reviewBuckets
) {

    public QualityEvalCaseResultDetail {
        caseId = clean(caseId);
        caseType = clean(caseType);
        status = clean(status);
        traceId = clean(traceId);
        agentRunId = clean(agentRunId);
        failureBuckets = failureBuckets == null ? List.of() : List.copyOf(failureBuckets);
        reviewBuckets = reviewBuckets == null ? List.of() : List.copyOf(reviewBuckets);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
