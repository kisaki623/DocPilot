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

class SelectorShadowThresholdEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentToolSelector primarySelector = new DocumentToolSelector();
    private final FakeLlmToolSelector shadowSelector = new FakeLlmToolSelector(new DocumentToolSelector());
    private final List<ToolDefinition> toolDefinitions = List.of(
            new ToolDefinition("document_status_tool", "Document status", "Checks parse status.", "{}", "{}", true),
            new ToolDefinition("document_summary_tool", "Document summary", "Returns summary.", "{}", "{}", true),
            new ToolDefinition("document_qa_tool", "Document QA", "Answers with citations.", "{}", "{}", true)
    );

    @Test
    void shouldProducePromotionCandidateFromOfflineShadowMetricsOnly() throws IOException {
        SelectorMetricsCollector metricsCollector = new SelectorMetricsCollector();
        List<String> mismatches = new ArrayList<>();

        for (EvalCase evalCase : loadEvalCases()) {
            ToolSelector.SelectResult primary = primarySelector.select(evalCase.task());
            LlmToolSelectionResult shadow = shadowSelector.selectWithPrompt(
                    evalCase.task(),
                    evalCase.parseReady(),
                    evalCase.hasSummary(),
                    toolDefinitions
            );

            metricsCollector.recordSuccess("fake", primary.decision(), shadow.decision());
            if (!primary.decision().equals(shadow.decision())) {
                mismatches.add("task=" + evalCase.task()
                        + ", primary=" + primary.decision()
                        + ", shadow=" + shadow.decision());
            }
        }

        SelectorMetricsSnapshot snapshot = metricsCollector.snapshot();
        SelectorShadowThresholdDecision decision = new SelectorShadowThresholdPolicy().evaluate(snapshot);

        if (!mismatches.isEmpty()) {
            System.out.println("Selector shadow threshold eval mismatches: " + mismatches);
        }
        assertTrue(decision.allowPromotionCandidate(), () -> "threshold decision should pass; reason=" + decision.reason());
        assertTrue(snapshot.matchRate() >= decision.minMatchRate());
        assertTrue(snapshot.failureRate() <= decision.maxFailureRate());
    }

    @Test
    void shouldExplainMismatchWithoutChangingPrimaryDecision() {
        SelectorMetricsCollector metricsCollector = new SelectorMetricsCollector();
        metricsCollector.recordSuccess("fake", "summary_tool", "summary_tool");
        metricsCollector.recordSuccess("fake", "summary_tool", "qa_tool");
        ToolSelector.SelectResult primaryBeforePolicy = primarySelector.select("summarize this document");

        SelectorShadowThresholdDecision decision = new SelectorShadowThresholdPolicy(2, 0.95d, 0.05d)
                .evaluate(metricsCollector.snapshot());
        ToolSelector.SelectResult primaryAfterPolicy = primarySelector.select("summarize this document");

        assertFalse(decision.allowPromotionCandidate());
        assertTrue(decision.reason().contains("matchRate"));
        assertTrue(metricsCollector.snapshot().decisionPairMetrics().containsKey("summary_tool->qa_tool"));
        assertTrue(primaryBeforePolicy.decision().equals(primaryAfterPolicy.decision()));
    }

    @Test
    void shouldTreatShadowFailureAsFailOpenEvaluationSignal() {
        SelectorMetricsCollector metricsCollector = new SelectorMetricsCollector();
        metricsCollector.recordSuccess("openai_compatible", "summary_tool", "summary_tool");
        metricsCollector.recordFailure("openai_compatible", "summary_tool");

        SelectorShadowThresholdDecision decision = new SelectorShadowThresholdPolicy(2, 0.90d, 0.05d)
                .evaluate(metricsCollector.snapshot());

        assertFalse(decision.allowPromotionCandidate());
        assertTrue(decision.reason().contains("failureRate"));
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
