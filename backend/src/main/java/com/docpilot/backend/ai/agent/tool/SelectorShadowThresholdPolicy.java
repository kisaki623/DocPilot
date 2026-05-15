package com.docpilot.backend.ai.agent.tool;

public class SelectorShadowThresholdPolicy {

    public static final int DEFAULT_MINIMUM_SAMPLES = 20;
    public static final double DEFAULT_MIN_MATCH_RATE = 0.95d;
    public static final double DEFAULT_MAX_FAILURE_RATE = 0.05d;

    private final int minimumSamples;
    private final double minMatchRate;
    private final double maxFailureRate;

    public SelectorShadowThresholdPolicy() {
        this(DEFAULT_MINIMUM_SAMPLES, DEFAULT_MIN_MATCH_RATE, DEFAULT_MAX_FAILURE_RATE);
    }

    public SelectorShadowThresholdPolicy(int minimumSamples, double minMatchRate, double maxFailureRate) {
        if (minimumSamples <= 0) {
            throw new IllegalArgumentException("minimumSamples must be positive.");
        }
        if (minMatchRate < 0.0d || minMatchRate > 1.0d) {
            throw new IllegalArgumentException("minMatchRate must be between 0.0 and 1.0.");
        }
        if (maxFailureRate < 0.0d || maxFailureRate > 1.0d) {
            throw new IllegalArgumentException("maxFailureRate must be between 0.0 and 1.0.");
        }
        this.minimumSamples = minimumSamples;
        this.minMatchRate = minMatchRate;
        this.maxFailureRate = maxFailureRate;
    }

    public SelectorShadowThresholdDecision evaluate(SelectorMetricsSnapshot snapshot) {
        if (snapshot == null || snapshot.totalCount() == 0) {
            return reject(0L, 0.0d, 0.0d, "No selector shadow samples recorded.");
        }

        long totalCount = snapshot.totalCount();
        double matchRate = snapshot.matchRate();
        double failureRate = snapshot.failureRate();
        if (totalCount < minimumSamples) {
            return reject(totalCount, matchRate, failureRate,
                    "Not enough selector shadow samples: totalCount=" + totalCount
                            + ", minimumSamples=" + minimumSamples + ".");
        }
        if (matchRate < minMatchRate) {
            return reject(totalCount, matchRate, failureRate,
                    "Selector shadow matchRate below threshold: matchRate=" + formatRate(matchRate)
                            + ", minMatchRate=" + formatRate(minMatchRate) + ".");
        }
        if (failureRate > maxFailureRate) {
            return reject(totalCount, matchRate, failureRate,
                    "Selector shadow failureRate above threshold: failureRate=" + formatRate(failureRate)
                            + ", maxFailureRate=" + formatRate(maxFailureRate) + ".");
        }
        return new SelectorShadowThresholdDecision(
                true,
                "Selector shadow metrics meet promotion candidate thresholds.",
                totalCount,
                matchRate,
                failureRate,
                minimumSamples,
                minMatchRate,
                maxFailureRate
        );
    }

    private SelectorShadowThresholdDecision reject(long totalCount,
                                                   double matchRate,
                                                   double failureRate,
                                                   String reason) {
        return new SelectorShadowThresholdDecision(
                false,
                reason,
                totalCount,
                matchRate,
                failureRate,
                minimumSamples,
                minMatchRate,
                maxFailureRate
        );
    }

    private String formatRate(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }
}
