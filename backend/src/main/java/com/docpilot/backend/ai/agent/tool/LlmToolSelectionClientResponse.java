package com.docpilot.backend.ai.agent.tool;

public record LlmToolSelectionClientResponse(String rawText,
                                             String provider,
                                             String model,
                                             boolean disabled,
                                             String errorMessage) {

    public LlmToolSelectionClientResponse {
        rawText = rawText == null ? "" : rawText;
        provider = normalize(provider, "unknown");
        model = normalize(model, "unknown");
        errorMessage = errorMessage == null ? "" : errorMessage.trim();
    }

    public static LlmToolSelectionClientResponse disabled(String errorMessage) {
        return new LlmToolSelectionClientResponse(
                "",
                "disabled",
                "disabled",
                true,
                errorMessage
        );
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
