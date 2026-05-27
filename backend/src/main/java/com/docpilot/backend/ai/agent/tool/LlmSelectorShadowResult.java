package com.docpilot.backend.ai.agent.tool;

public record LlmSelectorShadowResult(String primaryDecision,
                                      String shadowDecision,
                                      boolean matched,
                                      String primaryReason,
                                      String shadowReason) {

    public LlmSelectorShadowResult {
        primaryDecision = primaryDecision == null ? "" : primaryDecision.trim();
        shadowDecision = shadowDecision == null ? "" : shadowDecision.trim();
        primaryReason = primaryReason == null ? "" : primaryReason.trim();
        shadowReason = shadowReason == null ? "" : shadowReason.trim();
    }

    public static LlmSelectorShadowResult compare(String primaryDecision,
                                                  String shadowDecision,
                                                  String primaryReason,
                                                  String shadowReason) {
        String normalizedPrimary = primaryDecision == null ? "" : primaryDecision.trim();
        String normalizedShadow = shadowDecision == null ? "" : shadowDecision.trim();
        return new LlmSelectorShadowResult(
                normalizedPrimary,
                normalizedShadow,
                !normalizedPrimary.isEmpty() && normalizedPrimary.equals(normalizedShadow),
                primaryReason,
                shadowReason
        );
    }

    public static LlmSelectorShadowResult from(ToolSelector.SelectResult primarySelection,
                                               LlmToolSelectionResult shadowSelection) {
        if (primarySelection == null) {
            throw new IllegalArgumentException("primarySelection must not be null");
        }
        if (shadowSelection == null) {
            throw new IllegalArgumentException("shadowSelection must not be null");
        }
        return compare(
                primarySelection.decision(),
                shadowSelection.decision(),
                primarySelection.reason(),
                shadowSelection.routingReason()
        );
    }
}
