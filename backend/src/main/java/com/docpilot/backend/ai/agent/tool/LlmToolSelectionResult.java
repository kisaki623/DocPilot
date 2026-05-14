package com.docpilot.backend.ai.agent.tool;

import java.util.List;

public record LlmToolSelectionResult(String decision,
                                     List<String> toolNames,
                                     String routingReason,
                                     List<String> matchedKeywords,
                                     double confidence) {

    public LlmToolSelectionResult {
        decision = requireNonBlank(decision, "decision");
        if (toolNames == null || toolNames.isEmpty()) {
            throw new IllegalArgumentException("toolNames must not be empty");
        }
        toolNames = List.copyOf(toolNames);
        routingReason = routingReason == null ? "" : routingReason.trim();
        matchedKeywords = matchedKeywords == null ? List.of() : List.copyOf(matchedKeywords);
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
