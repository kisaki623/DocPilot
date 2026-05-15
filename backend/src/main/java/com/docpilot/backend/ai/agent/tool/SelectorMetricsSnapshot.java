package com.docpilot.backend.ai.agent.tool;

import java.time.Instant;
import java.util.Map;

public record SelectorMetricsSnapshot(long totalCount,
                                      long successCount,
                                      long failureCount,
                                      long matchedCount,
                                      long mismatchCount,
                                      double matchRate,
                                      double failureRate,
                                      Instant lastUpdatedTime,
                                      Map<String, ProviderMetrics> providerMetrics,
                                      Map<String, DecisionPairMetrics> decisionPairMetrics) {

    public long totalComparisons() {
        return totalCount;
    }

    public record ProviderMetrics(String provider,
                                  long totalCount,
                                  long successCount,
                                  long failureCount,
                                  long matchedCount,
                                  long mismatchCount,
                                  double matchRate,
                                  double failureRate) {
    }

    public record DecisionPairMetrics(String primaryDecision,
                                      String shadowDecision,
                                      long totalCount,
                                      long matchedCount,
                                      long mismatchCount) {
    }
}
