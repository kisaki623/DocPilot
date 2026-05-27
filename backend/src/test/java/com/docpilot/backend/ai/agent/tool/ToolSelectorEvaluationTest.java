package com.docpilot.backend.ai.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSelectorEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentToolSelector selector = new DocumentToolSelector();

    @Test
    void shouldMatchEvaluationCases() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/agent/tool-selector-eval-cases.json");
        assertNotNull(inputStream, "tool selector evaluation cases resource must exist");

        List<EvalCase> cases = objectMapper.readValue(inputStream, new TypeReference<>() {
        });
        assertTrue(cases.size() >= 20, "evaluation cases should cover at least 20 examples");

        int passed = 0;
        for (EvalCase evalCase : cases) {
            ToolSelector.SelectResult result = selector.select(evalCase.task());
            assertEquals(evalCase.expectedDecision(), result.decision(),
                    () -> "Unexpected decision for task=" + evalCase.task()
                            + ", parseReady=" + evalCase.parseReady()
                            + ", hasSummary=" + evalCase.hasSummary());
            passed++;
        }

        System.out.printf("Tool selector eval passed %d/%d cases%n", passed, cases.size());
    }

    private record EvalCase(String task, boolean parseReady, boolean hasSummary, String expectedDecision) {
    }
}
