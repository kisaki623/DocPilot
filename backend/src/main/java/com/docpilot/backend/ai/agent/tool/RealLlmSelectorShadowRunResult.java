package com.docpilot.backend.ai.agent.tool;

public record RealLlmSelectorShadowRunResult(boolean success,
                                             String primaryDecision,
                                             String shadowDecision,
                                             boolean matched,
                                             boolean shouldRecordMetrics,
                                             String errorMessage) {

    public RealLlmSelectorShadowRunResult {
        primaryDecision = primaryDecision == null ? "" : primaryDecision.trim();
        shadowDecision = shadowDecision == null ? "" : shadowDecision.trim();
        errorMessage = errorMessage == null ? "" : errorMessage.trim();
    }

    public static RealLlmSelectorShadowRunResult success(String primaryDecision, String shadowDecision) {
        String normalizedPrimary = primaryDecision == null ? "" : primaryDecision.trim();
        String normalizedShadow = shadowDecision == null ? "" : shadowDecision.trim();
        return new RealLlmSelectorShadowRunResult(
                true,
                normalizedPrimary,
                normalizedShadow,
                !normalizedPrimary.isEmpty() && normalizedPrimary.equals(normalizedShadow),
                true,
                ""
        );
    }

    public static RealLlmSelectorShadowRunResult failed(String primaryDecision, String errorMessage) {
        return new RealLlmSelectorShadowRunResult(
                false,
                primaryDecision,
                "",
                false,
                false,
                errorMessage
        );
    }
}
