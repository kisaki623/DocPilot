package com.docpilot.backend.ai.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowToolSelectorEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentToolSelector primarySelector = new DocumentToolSelector();
    private final FakeLlmToolSelector shadowSelector = new FakeLlmToolSelector(new DocumentToolSelector());
    private final SelectorMetricsCollector metricsCollector = new SelectorMetricsCollector();
    private final List<ToolDefinition> toolDefinitions = List.of(
            new ToolDefinition("document_status_tool", "Document status", "Checks parse status.", "{}", "{}", true),
            new ToolDefinition("document_summary_tool", "Document summary", "Returns summary.", "{}", "{}", true),
            new ToolDefinition("document_qa_tool", "Document QA", "Answers with citations.", "{}", "{}", true)
    );

    @Test
    void shouldComparePrimaryAndShadowSelectorsOffline() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/agent/tool-selector-eval-cases.json");
        assertNotNull(inputStream, "tool selector evaluation cases resource must exist");

        List<EvalCase> cases = objectMapper.readValue(inputStream, new TypeReference<>() {
        });
        assertFalse(cases.isEmpty(), "evaluation cases must not be empty");

        List<String> mismatches = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            ToolSelector.SelectResult primary = primarySelector.select(evalCase.task());
            LlmToolSelectionResult shadow = shadowSelector.selectWithPrompt(
                    evalCase.task(),
                    evalCase.parseReady(),
                    evalCase.hasSummary(),
                    toolDefinitions
            );

            assertFalse(shadow.decision().isBlank(), () -> "shadow decision must not be blank for task=" + evalCase.task());
            metricsCollector.record(primary.decision(), shadow.decision());
            if (!primary.decision().equals(shadow.decision())) {
                mismatches.add("task=" + evalCase.task()
                        + ", primary=" + primary.decision()
                        + ", shadow=" + shadow.decision());
            }
        }

        SelectorMetricsSnapshot snapshot = metricsCollector.snapshot();
        if (!mismatches.isEmpty()) {
            System.out.println("Shadow selector mismatches: " + mismatches);
        }
        System.out.printf("Shadow selector eval matchRate=%.4f, total=%d, matched=%d, mismatch=%d%n",
                snapshot.matchRate(),
                snapshot.totalComparisons(),
                snapshot.matchedCount(),
                snapshot.mismatchCount());

        assertTrue(snapshot.totalComparisons() == cases.size(), "all evaluation cases should be recorded");
        assertTrue(snapshot.matchRate() >= 0.95d,
                () -> "shadow selector matchRate is below threshold; mismatches=" + mismatches);
    }

    private record EvalCase(String task, boolean parseReady, boolean hasSummary, String expectedDecision) {
    }
}
