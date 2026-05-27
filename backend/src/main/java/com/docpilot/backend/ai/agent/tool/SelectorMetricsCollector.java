package com.docpilot.backend.ai.agent.tool;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class SelectorMetricsCollector {

    public static final String DEFAULT_PROVIDER = "unknown";

    private final AtomicLong totalCount = new AtomicLong();
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong matchedCount = new AtomicLong();
    private final AtomicLong mismatchCount = new AtomicLong();
    private final AtomicReference<Instant> lastUpdatedTime = new AtomicReference<>();
    private final ConcurrentHashMap<String, MutableProviderMetrics> providerMetrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DecisionPairKey, MutableDecisionPairMetrics> decisionPairMetrics = new ConcurrentHashMap<>();

    public void record(String primaryDecision, String shadowDecision) {
        recordSuccess(DEFAULT_PROVIDER, primaryDecision, shadowDecision);
    }

    public void recordSuccess(String provider, String primaryDecision, String shadowDecision) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedPrimaryDecision = normalizeDecision(primaryDecision);
        String normalizedShadowDecision = normalizeDecision(shadowDecision);
        boolean matched = normalizedPrimaryDecision.equals(normalizedShadowDecision);

        totalCount.incrementAndGet();
        successCount.incrementAndGet();
        if (matched) {
            matchedCount.incrementAndGet();
        } else {
            mismatchCount.incrementAndGet();
        }
        providerMetrics.computeIfAbsent(normalizedProvider, MutableProviderMetrics::new)
                .recordSuccess(matched);
        decisionPairMetrics.computeIfAbsent(
                        new DecisionPairKey(normalizedPrimaryDecision, normalizedShadowDecision),
                        key -> new MutableDecisionPairMetrics(key.primaryDecision(), key.shadowDecision()))
                .record(matched);
        lastUpdatedTime.set(Instant.now());
    }

    public void recordFailure(String provider, String primaryDecision) {
        String normalizedProvider = normalizeProvider(provider);

        totalCount.incrementAndGet();
        failureCount.incrementAndGet();
        providerMetrics.computeIfAbsent(normalizedProvider, MutableProviderMetrics::new)
                .recordFailure();
        lastUpdatedTime.set(Instant.now());
    }

    public SelectorMetricsSnapshot snapshot() {
        long total = totalCount.get();
        long success = successCount.get();
        long failure = failureCount.get();
        long matched = matchedCount.get();
        long mismatch = mismatchCount.get();
        double matchRate = success == 0 ? 0.0d : (double) matched / success;
        double failureRate = total == 0 ? 0.0d : (double) failure / total;
        return new SelectorMetricsSnapshot(
                total,
                success,
                failure,
                matched,
                mismatch,
                matchRate,
                failureRate,
                lastUpdatedTime.get(),
                immutableProviderMetrics(),
                immutableDecisionPairMetrics()
        );
    }

    private Map<String, SelectorMetricsSnapshot.ProviderMetrics> immutableProviderMetrics() {
        return providerMetrics.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().snapshot()));
    }

    private Map<String, SelectorMetricsSnapshot.DecisionPairMetrics> immutableDecisionPairMetrics() {
        return decisionPairMetrics.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().asMapKey(),
                        entry -> entry.getValue().snapshot()));
    }

    private String normalizeDecision(String decision) {
        return decision == null || decision.isBlank() ? "unknown" : decision.trim();
    }

    private String normalizeProvider(String provider) {
        return provider == null || provider.isBlank() ? DEFAULT_PROVIDER : provider.trim();
    }

    private record DecisionPairKey(String primaryDecision, String shadowDecision) {

        private String asMapKey() {
            return primaryDecision + "->" + shadowDecision;
        }
    }

    private static class MutableProviderMetrics {
        private final String provider;
        private final AtomicLong totalCount = new AtomicLong();
        private final AtomicLong successCount = new AtomicLong();
        private final AtomicLong failureCount = new AtomicLong();
        private final AtomicLong matchedCount = new AtomicLong();
        private final AtomicLong mismatchCount = new AtomicLong();

        private MutableProviderMetrics(String provider) {
            this.provider = provider;
        }

        private void recordSuccess(boolean matched) {
            totalCount.incrementAndGet();
            successCount.incrementAndGet();
            if (matched) {
                matchedCount.incrementAndGet();
            } else {
                mismatchCount.incrementAndGet();
            }
        }

        private void recordFailure() {
            totalCount.incrementAndGet();
            failureCount.incrementAndGet();
        }

        private SelectorMetricsSnapshot.ProviderMetrics snapshot() {
            long total = totalCount.get();
            long success = successCount.get();
            long failure = failureCount.get();
            long matched = matchedCount.get();
            long mismatch = mismatchCount.get();
            double matchRate = success == 0 ? 0.0d : (double) matched / success;
            double failureRate = total == 0 ? 0.0d : (double) failure / total;
            return new SelectorMetricsSnapshot.ProviderMetrics(
                    provider,
                    total,
                    success,
                    failure,
                    matched,
                    mismatch,
                    matchRate,
                    failureRate
            );
        }
    }

    private static class MutableDecisionPairMetrics {
        private final String primaryDecision;
        private final String shadowDecision;
        private final AtomicLong totalCount = new AtomicLong();
        private final AtomicLong matchedCount = new AtomicLong();
        private final AtomicLong mismatchCount = new AtomicLong();

        private MutableDecisionPairMetrics(String primaryDecision, String shadowDecision) {
            this.primaryDecision = primaryDecision;
            this.shadowDecision = shadowDecision;
        }

        private void record(boolean matched) {
            totalCount.incrementAndGet();
            if (matched) {
                matchedCount.incrementAndGet();
            } else {
                mismatchCount.incrementAndGet();
            }
        }

        private SelectorMetricsSnapshot.DecisionPairMetrics snapshot() {
            return new SelectorMetricsSnapshot.DecisionPairMetrics(
                    primaryDecision,
                    shadowDecision,
                    totalCount.get(),
                    matchedCount.get(),
                    mismatchCount.get()
            );
        }
    }
}
