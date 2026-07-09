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
        Map<String, String> attributes,
        List<String> buckets
) {

    public QualityTraceStepDetail {
        stepType = cleanIdentifier(stepType);
        status = clean(status);
        label = cleanLabel(label);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        flags = flags == null ? Map.of() : Map.copyOf(flags);
        attributes = attributes == null ? Map.of() : cleanAttributes(attributes);
        buckets = buckets == null ? List.of() : List.copyOf(buckets);
    }

    public QualityTraceStepDetail(
            String stepType,
            String status,
            String label,
            Map<String, Number> metrics,
            Map<String, Boolean> flags,
            List<String> buckets) {
        this(stepType, status, label, metrics, flags, Map.of(), buckets);
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

    private static Map<String, String> cleanAttributes(Map<String, String> values) {
        return values.entrySet().stream()
                .filter(entry -> !cleanIdentifier(entry.getKey()).isBlank())
                .map(entry -> Map.entry(cleanIdentifier(entry.getKey()), cleanAttributeValue(entry.getValue())))
                .filter(entry -> !entry.getValue().isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, ignored) -> left
                ));
    }

    private static String cleanAttributeValue(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank() || cleaned.length() > 160) {
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
                || lower.contains("apikey")
                || lower.contains("prompt")
                || lower.contains("document text")
                || lower.contains("evidence context")) {
            return "";
        }
        return cleaned;
    }
}
