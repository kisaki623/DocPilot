package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorMetricsDebugSnapshotTest {

    @Test
    void shouldFormatEmptySnapshot() {
        SelectorMetricsDebugSnapshot debugSnapshot = SelectorMetricsDebugSnapshot.from(new SelectorMetricsCollector().snapshot());

        assertEquals(0L, debugSnapshot.totalCount());
        assertEquals("0.0000", debugSnapshot.matchRate());
        assertEquals("0.0000", debugSnapshot.failureRate());
        assertTrue(debugSnapshot.providerAggregation().isEmpty());
        assertTrue(debugSnapshot.decisionAggregation().isEmpty());
        assertNull(debugSnapshot.promotionCandidate());
    }

    @Test
    void shouldFormatMatchedMismatchAndFailureSnapshot() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordSuccess("fake", "summary_tool", "summary_tool");
        collector.recordSuccess("fake", "summary_tool", "qa_tool");
        collector.recordFailure("openai_compatible", "qa_tool");

        SelectorMetricsDebugSnapshot debugSnapshot = SelectorMetricsDebugSnapshot.from(collector.snapshot());

        assertEquals(3L, debugSnapshot.totalCount());
        assertEquals(2L, debugSnapshot.successCount());
        assertEquals(1L, debugSnapshot.failureCount());
        assertEquals(1L, debugSnapshot.matchedCount());
        assertEquals(1L, debugSnapshot.mismatchCount());
        assertEquals("0.5000", debugSnapshot.matchRate());
        assertEquals("0.3333", debugSnapshot.failureRate());
    }

    @Test
    void shouldIncludeProviderAggregation() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordSuccess("fake", "summary_tool", "summary_tool");
        collector.recordFailure("openai_compatible", "qa_tool");

        SelectorMetricsDebugSnapshot debugSnapshot = SelectorMetricsDebugSnapshot.from(collector.snapshot());

        SelectorMetricsDebugSnapshot.ProviderView fakeProvider = debugSnapshot.providerAggregation().get("fake");
        SelectorMetricsDebugSnapshot.ProviderView openAiProvider =
                debugSnapshot.providerAggregation().get("openai_compatible");
        assertEquals(1L, fakeProvider.totalCount());
        assertEquals("1.0000", fakeProvider.matchRate());
        assertEquals(1L, openAiProvider.failureCount());
        assertEquals("1.0000", openAiProvider.failureRate());
    }

    @Test
    void shouldIncludeDecisionAggregation() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordSuccess("fake", "summary_tool", "summary_tool");
        collector.recordSuccess("fake", "summary_tool", "qa_tool");

        SelectorMetricsDebugSnapshot debugSnapshot = SelectorMetricsDebugSnapshot.from(collector.snapshot());

        SelectorMetricsDebugSnapshot.DecisionPairView matchedPair =
                debugSnapshot.decisionAggregation().get("summary_tool->summary_tool");
        SelectorMetricsDebugSnapshot.DecisionPairView mismatchPair =
                debugSnapshot.decisionAggregation().get("summary_tool->qa_tool");
        assertEquals(1L, matchedPair.matchedCount());
        assertEquals(1L, mismatchPair.mismatchCount());
    }

    @Test
    void shouldIncludeThresholdDecision() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        for (int i = 0; i < 20; i++) {
            collector.recordSuccess("fake", "qa_tool", "qa_tool");
        }
        SelectorShadowThresholdDecision thresholdDecision = new SelectorShadowThresholdPolicy()
                .evaluate(collector.snapshot());

        SelectorMetricsDebugSnapshot debugSnapshot =
                SelectorMetricsDebugSnapshot.from(collector.snapshot(), thresholdDecision);

        assertEquals(Boolean.TRUE, debugSnapshot.promotionCandidate());
        assertTrue(debugSnapshot.thresholdReason().contains("promotion candidate"));
        assertEquals(20, debugSnapshot.minimumSamples());
        assertEquals("0.9500", debugSnapshot.minMatchRate());
        assertEquals("0.0500", debugSnapshot.maxFailureRate());
    }

    @Test
    void shouldNotExposeSensitiveFields() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordSuccess("openai_compatible", "summary_tool", "summary_tool");

        String debugText = SelectorMetricsDebugSnapshot.from(collector.snapshot()).toString().toLowerCase();

        assertFalse(debugText.contains("apikey"));
        assertFalse(debugText.contains("baseurl"));
        assertFalse(debugText.contains("authorization"));
        assertFalse(debugText.contains("prompt"));
        assertFalse(debugText.contains("document content"));
        assertFalse(debugText.contains("raw response"));
    }

    @Test
    void shouldUseStableRateFormatting() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordSuccess("fake", "summary_tool", "summary_tool");
        collector.recordSuccess("fake", "summary_tool", "qa_tool");
        collector.recordSuccess("fake", "qa_tool", "qa_tool");

        SelectorMetricsDebugSnapshot debugSnapshot = SelectorMetricsDebugSnapshot.from(collector.snapshot());

        assertEquals("0.6667", debugSnapshot.matchRate());
        assertEquals("0.0000", debugSnapshot.failureRate());
    }

    @Test
    void shouldHandleNullSnapshot() {
        SelectorMetricsDebugSnapshot debugSnapshot = SelectorMetricsDebugSnapshot.from(null);

        assertEquals(0L, debugSnapshot.totalCount());
        assertEquals("0.0000", debugSnapshot.matchRate());
        assertTrue(debugSnapshot.providerAggregation().isEmpty());
    }
}
