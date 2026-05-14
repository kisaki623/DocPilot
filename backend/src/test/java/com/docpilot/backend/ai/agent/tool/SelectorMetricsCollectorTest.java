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
        assertEquals(2L, snapshot.totalComparisons());
        assertEquals(2L, snapshot.matchedCount());
        assertEquals(0L, snapshot.mismatchCount());
        assertEquals(1.0d, snapshot.matchRate());
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
        assertEquals(4L, snapshot.totalComparisons());
        assertEquals(2L, snapshot.matchedCount());
        assertEquals(2L, snapshot.mismatchCount());
        assertEquals(0.5d, snapshot.matchRate());
    }

    @Test
    void shouldReturnZeroRateWhenEmpty() {
        SelectorMetricsSnapshot snapshot = new SelectorMetricsCollector().snapshot();

        assertEquals(0L, snapshot.totalComparisons());
        assertEquals(0L, snapshot.matchedCount());
        assertEquals(0L, snapshot.mismatchCount());
        assertEquals(0.0d, snapshot.matchRate());
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
        assertEquals(200L, snapshot.totalComparisons());
        assertEquals(100L, snapshot.matchedCount());
        assertEquals(100L, snapshot.mismatchCount());
        assertEquals(0.5d, snapshot.matchRate());
        assertNotNull(snapshot.lastUpdatedTime());
    }
}
