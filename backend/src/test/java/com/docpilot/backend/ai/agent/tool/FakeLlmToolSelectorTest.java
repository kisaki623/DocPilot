package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeLlmToolSelectorTest {

    private final FakeLlmToolSelector selector = new FakeLlmToolSelector(new DocumentToolSelector());
    private final List<ToolDefinition> toolDefinitions = List.of(
            new ToolDefinition("document_status_tool", "Document status", "Checks parse status.", "{}", "{}", true),
            new ToolDefinition("document_summary_tool", "Document summary", "Returns summary.", "{}", "{}", true),
            new ToolDefinition("document_qa_tool", "Document QA", "Answers with citations.", "{}", "{}", true)
    );

    @Test
    void shouldMirrorSummaryTask() {
        assertShadowMatchesPrimary("summarize this document", true, false, "summary_tool");
    }

    @Test
    void shouldMirrorQaTask() {
        assertShadowMatchesPrimary("answer with evidence", true, true, "qa_tool");
    }

    @Test
    void shouldMirrorStatusTask() {
        assertShadowMatchesPrimary("show document status", true, true, "status_only");
    }

    @Test
    void shouldReturnStatusWhenParseReadyFalse() {
        LlmToolSelectionResult result = selector.selectWithPrompt("summarize this document", false, false, toolDefinitions);

        assertEquals("status_only", result.decision());
        assertFalse(result.decision().isBlank());
        assertEquals(List.of("document_status_tool"), result.toolNames());
    }

    private void assertShadowMatchesPrimary(String task, boolean parseReady, boolean hasSummary, String expectedDecision) {
        LlmToolSelectionResult shadow = selector.selectWithPrompt(task, parseReady, hasSummary, toolDefinitions);
        ToolSelector.SelectResult primary = new DocumentToolSelector().select(task);
        LlmSelectorShadowResult compare = LlmSelectorShadowResult.compare(
                primary.decision(),
                shadow.decision(),
                primary.reason(),
                shadow.routingReason()
        );

        assertEquals(expectedDecision, shadow.decision());
        assertFalse(shadow.decision().isBlank());
        assertTrue(compare.matched());
    }
}
