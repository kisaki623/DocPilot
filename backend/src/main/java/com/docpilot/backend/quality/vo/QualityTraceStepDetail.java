package com.docpilot.backend.quality.vo;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public record QualityTraceStepDetail(
        String stepType,
        String status,
        String label,
        Map<String, Number> metrics,
        Map<String, Boolean> flags,
        List<String> buckets
) {

    public QualityTraceStepDetail {
        stepType = cleanIdentifier(stepType);
        status = clean(status);
        label = cleanLabel(label);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        flags = flags == null ? Map.of() : Map.copyOf(flags);
        buckets = buckets == null ? List.of() : List.copyOf(buckets);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String cleanIdentifier(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank() || cleaned.length() > 64) {
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

    private static String cleanLabel(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank() || cleaned.length() > 96) {
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
                || lower.contains("api key")
                || lower.contains("api_key")
                || lower.contains("apikey")) {
            return "";
        }
        return cleaned;
    }
}
