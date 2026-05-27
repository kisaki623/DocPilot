package com.docpilot.backend.ai.agent.tool;

import java.time.Instant;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public record SelectorMetricsDebugSnapshot(long totalCount,
                                           long successCount,
                                           long failureCount,
                                           long matchedCount,
                                           long mismatchCount,
                                           String matchRate,
                                           String failureRate,
                                           Instant lastUpdatedTime,
                                           Map<String, ProviderView> providerAggregation,
                                           Map<String, DecisionPairView> decisionAggregation,
                                           Boolean promotionCandidate,
                                           String thresholdReason,
                                           Integer minimumSamples,
                                           String minMatchRate,
                                           String maxFailureRate) {

    public static SelectorMetricsDebugSnapshot from(SelectorMetricsSnapshot snapshot) {
        return from(snapshot, null);
    }

    public static SelectorMetricsDebugSnapshot from(SelectorMetricsSnapshot snapshot,
                                                    SelectorShadowThresholdDecision thresholdDecision) {
        if (snapshot == null) {
            return new SelectorMetricsDebugSnapshot(
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    formatRate(0.0d),
                    formatRate(0.0d),
                    null,
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    thresholdDecision == null ? null : thresholdDecision.allowPromotionCandidate(),
                    thresholdDecision == null ? null : thresholdDecision.reason(),
                    thresholdDecision == null ? null : thresholdDecision.minimumSamples(),
                    thresholdDecision == null ? null : formatRate(thresholdDecision.minMatchRate()),
                    thresholdDecision == null ? null : formatRate(thresholdDecision.maxFailureRate())
            );
        }

        return new SelectorMetricsDebugSnapshot(
                snapshot.totalCount(),
                snapshot.successCount(),
                snapshot.failureCount(),
                snapshot.matchedCount(),
                snapshot.mismatchCount(),
                formatRate(snapshot.matchRate()),
                formatRate(snapshot.failureRate()),
                snapshot.lastUpdatedTime(),
                providerViews(snapshot.providerMetrics()),
                decisionPairViews(snapshot.decisionPairMetrics()),
                thresholdDecision == null ? null : thresholdDecision.allowPromotionCandidate(),
                thresholdDecision == null ? null : thresholdDecision.reason(),
                thresholdDecision == null ? null : thresholdDecision.minimumSamples(),
                thresholdDecision == null ? null : formatRate(thresholdDecision.minMatchRate()),
                thresholdDecision == null ? null : formatRate(thresholdDecision.maxFailureRate())
        );
    }

    private static Map<String, ProviderView> providerViews(
            Map<String, SelectorMetricsSnapshot.ProviderMetrics> providerMetrics) {
        if (providerMetrics == null || providerMetrics.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, ProviderView> views = new TreeMap<>();
        providerMetrics.forEach((provider, metrics) -> views.put(provider, new ProviderView(
                metrics.provider(),
                metrics.totalCount(),
                metrics.successCount(),
                metrics.failureCount(),
                metrics.matchedCount(),
                metrics.mismatchCount(),
                formatRate(metrics.matchRate()),
                formatRate(metrics.failureRate())
        )));
        return Collections.unmodifiableMap(views);
    }

    private static Map<String, DecisionPairView> decisionPairViews(
            Map<String, SelectorMetricsSnapshot.DecisionPairMetrics> decisionPairMetrics) {
        if (decisionPairMetrics == null || decisionPairMetrics.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, DecisionPairView> views = new TreeMap<>();
        decisionPairMetrics.forEach((key, metrics) -> views.put(key, new DecisionPairView(
                metrics.primaryDecision(),
                metrics.shadowDecision(),
                metrics.totalCount(),
                metrics.matchedCount(),
                metrics.mismatchCount()
        )));
        return Collections.unmodifiableMap(views);
    }

    private static String formatRate(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    public record ProviderView(String provider,
                               long totalCount,
                               long successCount,
                               long failureCount,
                               long matchedCount,
                               long mismatchCount,
                               String matchRate,
                               String failureRate) {
    }

    public record DecisionPairView(String primaryDecision,
                                   String shadowDecision,
                                   long totalCount,
                                   long matchedCount,
                                   long mismatchCount) {
    }
}
