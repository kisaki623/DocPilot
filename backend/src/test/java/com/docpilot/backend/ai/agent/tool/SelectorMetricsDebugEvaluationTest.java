package com.docpilot.backend.ai.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorMetricsDebugEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentToolSelector primarySelector = new DocumentToolSelector();
    private final FakeLlmToolSelector shadowSelector = new FakeLlmToolSelector(new DocumentToolSelector());
    private final List<ToolDefinition> toolDefinitions = List.of(
            new ToolDefinition("document_status_tool", "Document status", "Checks parse status.", "{}", "{}", true),
            new ToolDefinition("document_summary_tool", "Document summary", "Returns summary.", "{}", "{}", true),
            new ToolDefinition("document_qa_tool", "Document QA", "Answers with citations.", "{}", "{}", true)
    );

    @Test
    void shouldBuildDebugDumpFromOfflineShadowEvaluation() throws IOException {
        SelectorMetricsCollector collector = runOfflineEvaluation();
        SelectorMetricsDebugReporter reporter = new SelectorMetricsDebugReporter(collector);

        SelectorMetricsDebugSnapshot dump = reporter.dump();

        assertEquals(loadEvalCases().size(), dump.totalCount());
        assertEquals("0.9583", dump.matchRate());
        assertTrue(dump.providerAggregation().containsKey("fake"));
        assertEquals(Boolean.TRUE, dump.promotionCandidate());
        assertTrue(dump.thresholdReason().contains("promotion candidate"));
    }

    @Test
    void shouldReportNotPromotionCandidateWhenSamplesAreInsufficient() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordSuccess("fake", "summary_tool", "summary_tool");
        SelectorMetricsDebugReporter reporter = new SelectorMetricsDebugReporter(collector);

        SelectorMetricsDebugSnapshot dump = reporter.dump();

        assertEquals(Boolean.FALSE, dump.promotionCandidate());
        assertTrue(dump.thresholdReason().contains("Not enough selector shadow samples"));
    }

    @Test
    void shouldReportPromotionCandidateWhenSamplesAndRatesMeetThresholds() {
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
    void shouldNotChangePrimaryRoutingWhenDumpingMetrics() {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        collector.recordSuccess("fake", "summary_tool", "qa_tool");
        SelectorMetricsDebugReporter reporter = new SelectorMetricsDebugReporter(collector);
        ToolSelector.SelectResult beforeDump = primarySelector.select("summarize this document");

        reporter.dump();
        ToolSelector.SelectResult afterDump = primarySelector.select("summarize this document");

        assertEquals(beforeDump.decision(), afterDump.decision());
        assertEquals("summary_tool", afterDump.decision());
    }

    @Test
    void shouldNotExposeSensitiveFieldsInEvaluationDump() throws IOException {
        SelectorMetricsDebugSnapshot dump = new SelectorMetricsDebugReporter(runOfflineEvaluation()).dump();

        String dumpText = dump.toString().toLowerCase();

        assertFalse(dumpText.contains("apikey"));
        assertFalse(dumpText.contains("baseurl"));
        assertFalse(dumpText.contains("authorization"));
        assertFalse(dumpText.contains("prompt"));
        assertFalse(dumpText.contains("document content"));
        assertFalse(dumpText.contains("raw response"));
    }

    private SelectorMetricsCollector runOfflineEvaluation() throws IOException {
        SelectorMetricsCollector collector = new SelectorMetricsCollector();
        for (EvalCase evalCase : loadEvalCases()) {
            ToolSelector.SelectResult primary = primarySelector.select(evalCase.task());
            LlmToolSelectionResult shadow = shadowSelector.selectWithPrompt(
                    evalCase.task(),
                    evalCase.parseReady(),
                    evalCase.hasSummary(),
                    toolDefinitions
            );
            collector.recordSuccess("fake", primary.decision(), shadow.decision());
        }
        return collector;
    }

    private List<EvalCase> loadEvalCases() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/agent/tool-selector-eval-cases.json");
        assertNotNull(inputStream, "tool selector evaluation cases resource must exist");
        return objectMapper.readValue(inputStream, new TypeReference<>() {
        });
    }

    private record EvalCase(String task, boolean parseReady, boolean hasSummary, String expectedDecision) {
    }
}
