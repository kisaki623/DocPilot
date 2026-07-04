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
        failureBuckets = failureBuckets == null ? List.of() : List.copyOf(failureBuckets);
        reviewBuckets = reviewBuckets == null ? List.of() : List.copyOf(reviewBuckets);
    }
}
