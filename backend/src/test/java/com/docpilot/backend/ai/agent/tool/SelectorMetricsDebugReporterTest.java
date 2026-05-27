package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorMetricsDebugReporterTest {

    @Test
    void shouldDumpEmptyMetrics() {
        SelectorMetricsDebugReporter reporter = new SelectorMetricsDebugReporter(new SelectorMetricsCollector());

        SelectorMetricsDebugSnapshot dump = reporter.dump();

        assertEquals(0L, dump.totalCount());
        assertEquals(Boolean.FALSE, dump.promotionCandidate());
        assertTrue(dump.thresholdReason().contains("No selector shadow samples"));
    }

    @Test
    void shouldDumpMatchedMetrics() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordSuccess("fake", "summary_tool", "summary_tool");
        SelectorMetricsDebugReporter reporter = new SelectorMetricsDebugReporter(collector);

        SelectorMetricsDebugSnapshot dump = reporter.dump();

        assertEquals(1L, dump.totalCount());
        assertEquals(1L, dump.matchedCount());
        assertEquals("1.0000", dump.matchRate());
        assertTrue(dump.providerAggregation().containsKey("fake"));
    }

    @Test
    void shouldDumpMismatchMetrics() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordSuccess("fake", "summary_tool", "qa_tool");
        SelectorMetricsDebugReporter reporter = new SelectorMetricsDebugReporter(collector);

        SelectorMetricsDebugSnapshot dump = reporter.dump();

        assertEquals(1L, dump.mismatchCount());
        assertEquals("0.0000", dump.matchRate());
        assertTrue(dump.decisionAggregation().containsKey("summary_tool->qa_tool"));
    }

    @Test
    void shouldDumpFailureMetrics() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordFailure("openai_compatible", "qa_tool");
        SelectorMetricsDebugReporter reporter = new SelectorMetricsDebugReporter(collector);

        SelectorMetricsDebugSnapshot dump = reporter.dump();

        assertEquals(1L, dump.failureCount());
        assertEquals("1.0000", dump.failureRate());
        assertEquals(1L, dump.providerAggregation().get("openai_compatible").failureCount());
    }

    @Test
    void shouldReportPromotionCandidateFalseWhenThresholdsAreNotMet() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordSuccess("fake", "summary_tool", "summary_tool");
        SelectorMetricsDebugReporter reporter = new SelectorMetricsDebugReporter(collector);

        SelectorMetricsDebugSnapshot dump = reporter.dump();

        assertEquals(Boolean.FALSE, dump.promotionCandidate());
        assertTrue(dump.thresholdReason().contains("Not enough selector shadow samples"));
    }

    @Test
    void shouldReportPromotionCandidateTrueWhenThresholdsAreMet() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        for (int i = 0; i < 20; i++) {
            collector.recordSuccess("fake", "qa_tool", "qa_tool");
        }
        SelectorMetricsDebugReporter reporter = new SelectorMetricsDebugReporter(collector);

        SelectorMetricsDebugSnapshot dump = reporter.dump();

        assertEquals(Boolean.TRUE, dump.promotionCandidate());
        assertEquals("1.0000", dump.matchRate());
        assertEquals("0.0000", dump.failureRate());
    }

    @Test
    void shouldNotChangeMetricsWhenDumping() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordSuccess("fake", "summary_tool", "summary_tool");
        SelectorMetricsDebugReporter reporter = new SelectorMetricsDebugReporter(collector);
        SelectorMetricsSnapshot before = collector.snapshot();

        reporter.dump();
        reporter.dump();
        SelectorMetricsSnapshot after = collector.snapshot();

        assertEquals(before.totalCount(), after.totalCount());
        assertEquals(before.successCount(), after.successCount());
        assertEquals(before.matchedCount(), after.matchedCount());
    }

    @Test
    void shouldNotExposeSensitiveFields() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordSuccess("openai_compatible", "summary_tool", "summary_tool");
        SelectorMetricsDebugReporter reporter = new SelectorMetricsDebugReporter(collector);

        String dumpText = reporter.dump().toString().toLowerCase();

        assertFalse(dumpText.contains("apikey"));
        assertFalse(dumpText.contains("baseurl"));
        assertFalse(dumpText.contains("authorization"));
        assertFalse(dumpText.contains("prompt"));
        assertFalse(dumpText.contains("document content"));
        assertFalse(dumpText.contains("raw response"));
    }
}
