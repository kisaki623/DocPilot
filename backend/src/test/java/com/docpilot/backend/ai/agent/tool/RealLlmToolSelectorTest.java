package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RealLlmToolSelectorTest {

    private final List<ToolDefinition> toolDefinitions = List.of(
            new ToolDefinition("document_status_tool", "Document status", "Checks parse status.", "{}", "{}", true),
            new ToolDefinition("document_summary_tool", "Document summary", "Returns summary.", "{}", "{}", true),
            new ToolDefinition("document_qa_tool", "Document QA", "Answers with citations.", "{}", "{}", true)
    );
    private final LlmToolSelectionPromptBuilder promptBuilder = new LlmToolSelectionPromptBuilder();
    private final LlmToolSelectionParser parser = new LlmToolSelectionParser(Set.of(
            "document_status_tool",
            "document_summary_tool",
            "document_qa_tool"
    ));

    @Test
    void shouldParseSummaryDecisionFromClientResponse() {
        RealLlmToolSelector selector = new RealLlmToolSelector(promptBuilder, prompt -> response("""
                {"decision":"summary_tool","toolNames":["document_status_tool","document_summary_tool"],"routingReason":"summary requested","matchedKeywords":["summary"],"confidence":0.88}
                """), parser);

        LlmToolSelectionResult result = selector.selectWithPrompt("summarize this document", true, false, toolDefinitions);

        assertEquals("summary_tool", result.decision());
        assertEquals(List.of("document_status_tool", "document_summary_tool"), result.toolNames());
    }

    @Test
    void shouldParseQaDecisionFromClientResponse() {
        RealLlmToolSelector selector = new RealLlmToolSelector(promptBuilder, prompt -> response("""
                The decision follows.
                {"decision":"qa_tool","toolNames":["document_status_tool","document_qa_tool"],"routingReason":"evidence requested","matchedKeywords":["evidence"],"confidence":0.93}
                """), parser);

        LlmToolSelectionResult result = selector.selectWithPrompt("answer with evidence", true, true, toolDefinitions);

        assertEquals("qa_tool", result.decision());
        assertEquals(List.of("document_status_tool", "document_qa_tool"), result.toolNames());
    }

    @Test
    void shouldFailClearlyWhenClientDisabled() {
        RealLlmToolSelector selector = new RealLlmToolSelector(
                promptBuilder,
                new DisabledLlmToolSelectionClient(),
                parser
        );

        assertThrows(IllegalStateException.class,
                () -> selector.selectWithPrompt("summarize this document", true, false, toolDefinitions));
    }

    @Test
    void shouldFailClearlyForInvalidJson() {
        RealLlmToolSelector selector = new RealLlmToolSelector(
                promptBuilder,
                prompt -> response("not json"),
                parser
        );

        assertThrows(IllegalArgumentException.class,
                () -> selector.selectWithPrompt("summarize this document", true, false, toolDefinitions));
    }

    @Test
    void shouldNotFallbackToKeywordSelectorWhenClientFails() {
        RealLlmToolSelector selector = new RealLlmToolSelector(
                promptBuilder,
                prompt -> {
                    throw new IllegalStateException("client unavailable");
                },
                parser
        );

        assertThrows(IllegalStateException.class,
                () -> selector.selectWithPrompt("summarize this document", true, false, toolDefinitions));
    }

    private LlmToolSelectionClientResponse response(String rawText) {
        return new LlmToolSelectionClientResponse(
                rawText,
                "fake-provider",
                "fake-model",
                false,
                ""
        );
    }
}
