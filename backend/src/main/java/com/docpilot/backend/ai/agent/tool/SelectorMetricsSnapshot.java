package com.docpilot.backend.ai.agent.tool;

import java.time.Instant;

public record SelectorMetricsSnapshot(long totalComparisons,
                                      long matchedCount,
                                      long mismatchCount,
                                      double matchRate,
                                      Instant lastUpdatedTime) {
}
