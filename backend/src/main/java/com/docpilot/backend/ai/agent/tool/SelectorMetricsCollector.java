package com.docpilot.backend.ai.agent.tool;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SelectorMetricsCollector {

    private final AtomicLong totalComparisons = new AtomicLong();
    private final AtomicLong matchedCount = new AtomicLong();
    private final AtomicReference<Instant> lastUpdatedTime = new AtomicReference<>();

    public void record(String primaryDecision, String shadowDecision) {
        totalComparisons.incrementAndGet();
        if (normalize(primaryDecision).equals(normalize(shadowDecision))) {
            matchedCount.incrementAndGet();
        }
        lastUpdatedTime.set(Instant.now());
    }

    public SelectorMetricsSnapshot snapshot() {
        long total = totalComparisons.get();
        long matched = matchedCount.get();
        long mismatch = total - matched;
        double matchRate = total == 0 ? 0.0d : (double) matched / total;
        return new SelectorMetricsSnapshot(
                total,
                matched,
                mismatch,
                matchRate,
                lastUpdatedTime.get()
        );
    }

    private String normalize(String decision) {
        return decision == null ? "" : decision.trim();
    }
}
