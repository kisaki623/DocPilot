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
}
