package com.docpilot.backend.ai.agent.tool;

public record SelectorShadowThresholdDecision(boolean allowPromotionCandidate,
                                              String reason,
                                              long totalCount,
                                              double matchRate,
                                              double failureRate,
                                              int minimumSamples,
                                              double minMatchRate,
                                              double maxFailureRate) {
}
