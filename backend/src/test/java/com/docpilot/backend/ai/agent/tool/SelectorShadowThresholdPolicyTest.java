package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorShadowThresholdPolicyTest {

    @Test
    void shouldRejectWhenSampleCountIsTooSmall() {
        SelectorShadowThresholdPolicy policy = new SelectorShadowThresholdPolicy();
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        recordMatches(collector, 19);

        SelectorShadowThresholdDecision decision = policy.evaluate(collector.snapshot());

        assertFalse(decision.allowPromotionCandidate());
        assertTrue(decision.reason().contains("Not enough"));
    }

    @Test
    void shouldRejectWhenMatchRateIsTooLow() {
        SelectorShadowThresholdPolicy policy = new SelectorShadowThresholdPolicy();
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        recordMatches(collector, 18);
        recordMismatches(collector, 2);

        SelectorShadowThresholdDecision decision = policy.evaluate(collector.snapshot());

        assertFalse(decision.allowPromotionCandidate());
        assertTrue(decision.reason().contains("matchRate"));
    }

    @Test
    void shouldRejectWhenFailureRateIsTooHigh() {
        SelectorShadowThresholdPolicy policy = new SelectorShadowThresholdPolicy();
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        recordMatches(collector, 18);
        collector.recordFailure("openai_compatible", "summary_tool");
        collector.recordFailure("openai_compatible", "qa_tool");

        SelectorShadowThresholdDecision decision = policy.evaluate(collector.snapshot());

        assertFalse(decision.allowPromotionCandidate());
        assertTrue(decision.reason().contains("failureRate"));
    }

    @Test
    void shouldAllowPromotionCandidateWhenMetricsMeetThresholds() {
        SelectorShadowThresholdPolicy policy = new SelectorShadowThresholdPolicy();
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        recordMatches(collector, 20);

        SelectorShadowThresholdDecision decision = policy.evaluate(collector.snapshot());

        assertTrue(decision.allowPromotionCandidate());
        assertTrue(decision.reason().contains("meet"));
    }

    @Test
    void shouldRejectEmptySnapshot() {
        SelectorShadowThresholdPolicy policy = new SelectorShadowThresholdPolicy();

        SelectorShadowThresholdDecision decision = policy.evaluate(new SelectorMetricsCollector().snapshot());

        assertFalse(decision.allowPromotionCandidate());
        assertTrue(decision.reason().contains("No selector shadow samples"));
    }

    @Test
    void shouldAllowBoundaryValuesEqualToThresholds() {
        SelectorShadowThresholdPolicy policy = new SelectorShadowThresholdPolicy(20, 0.95d, 0.05d);
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        recordMatches(collector, 19);
        recordMismatches(collector, 1);

        SelectorShadowThresholdDecision decision = policy.evaluate(collector.snapshot());

        assertTrue(decision.allowPromotionCandidate());
        assertTrue(decision.reason().contains("meet"));
    }

    @Test
    void shouldReturnReadableReason() {
        SelectorShadowThresholdPolicy policy = new SelectorShadowThresholdPolicy();
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        recordMatches(collector, 10);

        SelectorShadowThresholdDecision decision = policy.evaluate(collector.snapshot());

        assertFalse(decision.reason().isBlank());
        assertTrue(decision.reason().contains("minimumSamples"));
    }

    @Test
    void shouldOnlyReturnDecisionWithoutChangingRoutingState() {
        SelectorShadowThresholdPolicy policy = new SelectorShadowThresholdPolicy();
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        recordMatches(collector, 20);

        SelectorShadowThresholdDecision decision = policy.evaluate(collector.snapshot());

        assertTrue(decision.allowPromotionCandidate());
        assertTrue(decision.toString().contains("allowPromotionCandidate=true"));
        assertFalse(decision.toString().contains("productionRouting"));
    }

    private void recordMatches(SelectorMetricsCollector collector, int count) {
        for (int i = 0; i < count; i++) {
            collector.recordSuccess("openai_compatible", "summary_tool", "summary_tool");
        }
    }

    private void recordMismatches(SelectorMetricsCollector collector, int count) {
        for (int i = 0; i < count; i++) {
            collector.recordSuccess("openai_compatible", "summary_tool", "qa_tool");
        }
    }
}
