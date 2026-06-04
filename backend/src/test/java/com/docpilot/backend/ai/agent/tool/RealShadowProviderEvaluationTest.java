package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealShadowProviderEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentToolSelector primarySelector = new DocumentToolSelector();
    private final RealLlmSelectorShadowRunner shadowRunner = new RealLlmSelectorShadowRunner(
            new RealLlmToolSelectorFactory(
                    new LlmToolSelectionClientFactory(),
                    new LlmToolSelectionPromptBuilder(),
                    new LlmToolSelectionParser(Set.of(
                            "document_status_tool",
                            "document_summary_tool",
                            "document_qa_tool",
                            DocumentRagQaTool.TOOL_NAME
                    ))
            ),
            fakeProviderProperties()
    );
    private final List<ToolDefinition> toolDefinitions = List.of(
            new ToolDefinition("document_status_tool", "Document status", "Checks parse status.", "{}", "{}", true),
            new ToolDefinition("document_summary_tool", "Document summary", "Returns summary.", "{}", "{}", true),
            new ToolDefinition("document_qa_tool", "Document QA", "Answers with citations.", "{}", "{}", true),
            new ToolDefinition(DocumentRagQaTool.TOOL_NAME, "RAG QA", "Answers with RAG citations.", "{}", "{}", true)
    );

    @Test
    void shouldEvaluateFakeProviderRealShadowOffline() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/agent/tool-selector-eval-cases.json");
        assertNotNull(inputStream, "tool selector evaluation cases resource must exist");

        List<EvalCase> cases = objectMapper.readValue(inputStream, new TypeReference<>() {
        });
        assertFalse(cases.isEmpty(), "evaluation cases must not be empty");

        int successCount = 0;
        int matchedCount = 0;
        List<String> mismatches = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (EvalCase evalCase : cases) {
            ToolSelector.SelectResult primary = primarySelector.select(evalCase.task());
            RealLlmSelectorShadowRunResult shadow = shadowRunner.run(
                    primary.decision(),
                    evalCase.task(),
                    evalCase.parseReady(),
                    evalCase.hasSummary(),
                    toolDefinitions
            );

            if (!shadow.success()) {
                failures.add("task=" + evalCase.task() + ", error=" + shadow.errorMessage());
                continue;
            }

            successCount++;
            if (shadow.matched()) {
                matchedCount++;
            } else {
                mismatches.add("task=" + evalCase.task()
                        + ", primary=" + primary.decision()
                        + ", shadow=" + shadow.shadowDecision());
            }
        }

        double matchRate = matchedCount / (double) cases.size();
        double successRate = successCount / (double) cases.size();
        if (!failures.isEmpty()) {
            System.out.println("Real shadow fake provider failures: " + failures);
        }
        if (!mismatches.isEmpty()) {
            System.out.println("Real shadow fake provider mismatches: " + mismatches);
        }
        System.out.printf(
                "Real shadow fake provider eval matchRate=%.4f, successRate=%.4f, total=%d, success=%d, matched=%d, mismatch=%d, failures=%d%n",
                matchRate,
                successRate,
                cases.size(),
                successCount,
                matchedCount,
                mismatches.size(),
                failures.size()
        );

        assertTrue(successCount > 0, "fake provider shadow runner should succeed for non-blank cases");
        assertTrue(matchRate >= 0.90d,
                () -> "fake provider real shadow matchRate is below threshold; mismatches="
                        + mismatches + ", failures=" + failures);
    }

    private AgentSelectorProperties fakeProviderProperties() {
        AgentSelectorProperties properties = new AgentSelectorProperties();
        properties.setLlmProvider(AgentSelectorProperties.PROVIDER_FAKE);
        return properties;
    }

    private record EvalCase(String task, boolean parseReady, boolean hasSummary, String expectedDecision) {
    }
}
