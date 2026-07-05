package com.docpilot.backend.quality.vo;

import java.util.List;
import java.util.Locale;

public record QualityTraceReference(
        String caseId,
        String caseType,
        String status,
        String gateName,
        String traceId,
        String agentRunId,
        String conversationId,
        List<String> failureBuckets,
        List<String> reviewBuckets
) {

    public QualityTraceReference {
        caseId = clean(caseId);
        caseType = clean(caseType);
        status = clean(status);
        gateName = clean(gateName);
        traceId = cleanIdentifier(traceId);
        agentRunId = cleanIdentifier(agentRunId);
        conversationId = cleanIdentifier(conversationId);
        failureBuckets = failureBuckets == null ? List.of() : List.copyOf(failureBuckets);
        reviewBuckets = reviewBuckets == null ? List.of() : List.copyOf(reviewBuckets);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String cleanIdentifier(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank() || cleaned.length() > 128) {
            return "";
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.contains("bearer ")
                || lower.contains("sk-")
                || lower.contains("jdbc:")
                || lower.contains("http://")
                || lower.contains("https://")
                || lower.contains("password")
                || lower.contains("secret")
                || lower.contains("api_key")
                || lower.contains("apikey")) {
            return "";
        }
        return cleaned;
    }
}
