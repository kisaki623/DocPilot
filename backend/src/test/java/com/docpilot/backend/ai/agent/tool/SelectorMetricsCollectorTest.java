package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorMetricsCollectorTest {

    @Test
    void shouldRecordAllMatches() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();

        collector.record("summary_tool", "summary_tool");
        collector.record("qa_tool", "qa_tool");

        SelectorMetricsSnapshot snapshot = collector.snapshot();
        assertEquals(2L, snapshot.totalCount());
        assertEquals(2L, snapshot.totalComparisons());
        assertEquals(2L, snapshot.successCount());
        assertEquals(0L, snapshot.failureCount());
        assertEquals(2L, snapshot.matchedCount());
        assertEquals(0L, snapshot.mismatchCount());
        assertEquals(1.0d, snapshot.matchRate());
        assertEquals(0.0d, snapshot.failureRate());
        assertNotNull(snapshot.lastUpdatedTime());
    }

    @Test
    void shouldRecordPartialMismatch() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();

        collector.record("summary_tool", "summary_tool");
        collector.record("summary_tool", "qa_tool");
        collector.record("status_only", "qa_tool");
        collector.record("qa_tool", "qa_tool");

        SelectorMetricsSnapshot snapshot = collector.snapshot();
        assertEquals(4L, snapshot.totalCount());
        assertEquals(4L, snapshot.totalComparisons());
        assertEquals(4L, snapshot.successCount());
        assertEquals(0L, snapshot.failureCount());
        assertEquals(2L, snapshot.matchedCount());
        assertEquals(2L, snapshot.mismatchCount());
        assertEquals(0.5d, snapshot.matchRate());
        assertEquals(0.0d, snapshot.failureRate());
    }

    @Test
    void shouldRecordFailures() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();

        collector.recordSuccess("openai_compatible", "summary_tool", "summary_tool");
        collector.recordFailure("openai_compatible", "summary_tool");

        SelectorMetricsSnapshot snapshot = collector.snapshot();
        assertEquals(2L, snapshot.totalCount());
        assertEquals(1L, snapshot.successCount());
        assertEquals(1L, snapshot.failureCount());
        assertEquals(1L, snapshot.matchedCount());
        assertEquals(0L, snapshot.mismatchCount());
        assertEquals(1.0d, snapshot.matchRate());
        assertEquals(0.5d, snapshot.failureRate());
    }

    @Test
    void shouldReturnZeroRateWhenEmpty() {
        SelectorMetricsSnapshot snapshot = new SelectorMetricsCollector().snapshot();

        assertEquals(0L, snapshot.totalCount());
        assertEquals(0L, snapshot.totalComparisons());
        assertEquals(0L, snapshot.successCount());
        assertEquals(0L, snapshot.failureCount());
        assertEquals(0L, snapshot.matchedCount());
        assertEquals(0L, snapshot.mismatchCount());
        assertEquals(0.0d, snapshot.matchRate());
        assertEquals(0.0d, snapshot.failureRate());
        assertTrue(snapshot.providerMetrics().isEmpty());
        assertTrue(snapshot.decisionPairMetrics().isEmpty());
    }

    @Test
    void shouldAggregateByProvider() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();

        collector.recordSuccess("fake", "summary_tool", "summary_tool");
        collector.recordSuccess("openai_compatible", "summary_tool", "qa_tool");
        collector.recordFailure("openai_compatible", "summary_tool");

        SelectorMetricsSnapshot snapshot = collector.snapshot();
        SelectorMetricsSnapshot.ProviderMetrics fakeMetrics = snapshot.providerMetrics().get("fake");
        SelectorMetricsSnapshot.ProviderMetrics openAiMetrics = snapshot.providerMetrics().get("openai_compatible");

        assertEquals(1L, fakeMetrics.totalCount());
        assertEquals(1L, fakeMetrics.successCount());
        assertEquals(0L, fakeMetrics.failureCount());
        assertEquals(1L, fakeMetrics.matchedCount());
        assertEquals(1.0d, fakeMetrics.matchRate());

        assertEquals(2L, openAiMetrics.totalCount());
        assertEquals(1L, openAiMetrics.successCount());
        assertEquals(1L, openAiMetrics.failureCount());
        assertEquals(0L, openAiMetrics.matchedCount());
        assertEquals(1L, openAiMetrics.mismatchCount());
        assertEquals(0.0d, openAiMetrics.matchRate());
        assertEquals(0.5d, openAiMetrics.failureRate());
    }

    @Test
    void shouldAggregateByDecisionPair() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();

        collector.recordSuccess("fake", "summary_tool", "summary_tool");
        collector.recordSuccess("fake", "summary_tool", "qa_tool");
        collector.recordSuccess("openai_compatible", "summary_tool", "qa_tool");

        SelectorMetricsSnapshot snapshot = collector.snapshot();
        SelectorMetricsSnapshot.DecisionPairMetrics matchedPair =
                snapshot.decisionPairMetrics().get("summary_tool->summary_tool");
        SelectorMetricsSnapshot.DecisionPairMetrics mismatchPair =
                snapshot.decisionPairMetrics().get("summary_tool->qa_tool");

        assertEquals("summary_tool", matchedPair.primaryDecision());
        assertEquals("summary_tool", matchedPair.shadowDecision());
        assertEquals(1L, matchedPair.totalCount());
        assertEquals(1L, matchedPair.matchedCount());
        assertEquals(0L, matchedPair.mismatchCount());

        assertEquals("summary_tool", mismatchPair.primaryDecision());
        assertEquals("qa_tool", mismatchPair.shadowDecision());
        assertEquals(2L, mismatchPair.totalCount());
        assertEquals(0L, mismatchPair.matchedCount());
        assertEquals(2L, mismatchPair.mismatchCount());
    }

    @Test
    void shouldNotExposeSensitiveFields() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();

        collector.recordSuccess("openai_compatible", "summary_tool", "summary_tool");

        SelectorMetricsSnapshot snapshot = collector.snapshot();
        assertTrue(snapshot.toString().contains("openai_compatible"));
        assertTrue(snapshot.toString().contains("summary_tool"));
        assertTrue(snapshot.toString().contains("providerMetrics"));
        assertTrue(snapshot.toString().contains("decisionPairMetrics"));
        assertTrue(!snapshot.toString().contains("apiKey"));
        assertTrue(!snapshot.toString().contains("baseUrl"));
        assertTrue(!snapshot.toString().contains("prompt"));
        assertTrue(!snapshot.toString().contains("documentContent"));
    }

    @Test
    void shouldHandleConcurrentRecords() throws Exception {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            final int index = i;
            tasks.add(() -> {
                if (index % 2 == 0) {
                    collector.record("summary_tool", "summary_tool");
                } else {
                    collector.record("summary_tool", "qa_tool");
                }
                return null;
            });
        }

        executorService.invokeAll(tasks);
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));

        SelectorMetricsSnapshot snapshot = collector.snapshot();
        assertEquals(200L, snapshot.totalCount());
        assertEquals(200L, snapshot.totalComparisons());
        assertEquals(200L, snapshot.successCount());
        assertEquals(0L, snapshot.failureCount());
        assertEquals(100L, snapshot.matchedCount());
        assertEquals(100L, snapshot.mismatchCount());
        assertEquals(0.5d, snapshot.matchRate());
        assertEquals(0.0d, snapshot.failureRate());
        assertNotNull(snapshot.lastUpdatedTime());
    }
}
