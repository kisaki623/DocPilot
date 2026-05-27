package com.docpilot.backend.ai.agent.tool;

import java.util.List;

public record ToolExecutionDecision(String primaryDecision,
                                    String llmDecision,
                                    String finalDecision,
                                    boolean fallbackUsed,
                                    String fallbackReason,
                                    String provider,
                                    boolean matched,
                                    String routingReason,
                                    List<String> matchedKeywords,
                                    String toolSelectionSource) {

    public static final String SOURCE_KEYWORD = "keyword";
    public static final String SOURCE_LLM_EXECUTE = "llm_execute";
    public static final String SOURCE_LLM_EXECUTE_FALLBACK = "llm_execute_fallback";

    public ToolExecutionDecision {
        primaryDecision = requireNonBlank(primaryDecision, "primaryDecision");
        finalDecision = requireNonBlank(finalDecision, "finalDecision");
        llmDecision = llmDecision == null ? "" : llmDecision.trim();
        fallbackReason = fallbackReason == null ? "" : fallbackReason.trim();
        provider = provider == null ? "" : provider.trim();
        routingReason = routingReason == null ? "" : routingReason.trim();
        matchedKeywords = matchedKeywords == null ? List.of() : List.copyOf(matchedKeywords);
        toolSelectionSource = requireNonBlank(toolSelectionSource, "toolSelectionSource");
    }

    public static ToolExecutionDecision keyword(ToolSelector.SelectResult primarySelection) {
        return new ToolExecutionDecision(
                primarySelection.decision(),
                "",
                primarySelection.decision(),
                false,
                "",
                "",
                true,
                primarySelection.reason(),
                primarySelection.matchedKeywords(),
                SOURCE_KEYWORD
        );
    }

    public static ToolExecutionDecision llmExecute(ToolSelector.SelectResult primarySelection,
                                                   LlmToolSelectionResult llmSelection,
                                                   String provider) {
        return new ToolExecutionDecision(
                primarySelection.decision(),
                llmSelection.decision(),
                llmSelection.decision(),
                false,
                "",
                provider,
                primarySelection.decision().equals(llmSelection.decision()),
                llmSelection.routingReason(),
                llmSelection.matchedKeywords(),
                SOURCE_LLM_EXECUTE
        );
    }

    public static ToolExecutionDecision fallback(ToolSelector.SelectResult primarySelection,
                                                 String llmDecision,
                                                 String provider,
                                                 String fallbackReason) {
        return new ToolExecutionDecision(
                primarySelection.decision(),
                llmDecision,
                primarySelection.decision(),
                true,
                fallbackReason,
                provider,
                false,
                buildFallbackRoutingReason(primarySelection, fallbackReason),
                primarySelection.matchedKeywords(),
                SOURCE_LLM_EXECUTE_FALLBACK
        );
    }

    private static String buildFallbackRoutingReason(ToolSelector.SelectResult primarySelection, String fallbackReason) {
        String reason = primarySelection.reason();
        if (reason == null || reason.isBlank()) {
            return "LLM execute fallback to keyword selector: " + safeReason(fallbackReason);
        }
        return reason + " | LLM execute fallback: " + safeReason(fallbackReason);
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown reason";
        }
        return reason.trim();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
