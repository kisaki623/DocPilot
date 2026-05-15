package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealLlmSelectorShadowRunnerTest {

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
    void shouldReturnFailedResultWhenClientDisabled() {
        RealLlmSelectorShadowRunner runner = runnerWithClient(new DisabledLlmToolSelectionClient());

        RealLlmSelectorShadowRunResult result = runner.run(
                "summary_tool",
                "summarize this document",
                true,
                false,
                toolDefinitions
        );

        assertFalse(result.success());
        assertFalse(result.shouldRecordMetrics());
        assertFalse(result.errorMessage().isBlank());
        assertEquals("summary_tool", result.primaryDecision());
    }

    @Test
    void shouldReturnMatchedSuccessForValidFakeClient() {
        RealLlmSelectorShadowRunner runner = runnerWithClient(prompt -> response("""
                {"decision":"summary_tool","toolNames":["document_status_tool","document_summary_tool"],"routingReason":"summary requested","matchedKeywords":["summary"],"confidence":0.88}
                """));

        RealLlmSelectorShadowRunResult result = runner.run(
                "summary_tool",
                "summarize this document",
                true,
                false,
                toolDefinitions
        );

        assertTrue(result.success());
        assertTrue(result.matched());
        assertTrue(result.shouldRecordMetrics());
        assertEquals("summary_tool", result.shadowDecision());
    }

    @Test
    void shouldReturnMismatchSuccessForDifferentShadowDecision() {
        RealLlmSelectorShadowRunner runner = runnerWithClient(prompt -> response("""
                {"decision":"qa_tool","toolNames":["document_status_tool","document_qa_tool"],"routingReason":"evidence requested","matchedKeywords":["evidence"],"confidence":0.90}
                """));

        RealLlmSelectorShadowRunResult result = runner.run(
                "summary_tool",
                "answer with evidence",
                true,
                true,
                toolDefinitions
        );

        assertTrue(result.success());
        assertFalse(result.matched());
        assertTrue(result.shouldRecordMetrics());
        assertEquals("qa_tool", result.shadowDecision());
    }

    @Test
    void shouldReturnFailedResultForInvalidJson() {
        RealLlmSelectorShadowRunner runner = runnerWithClient(prompt -> response("not json"));

        RealLlmSelectorShadowRunResult result = runner.run(
                "summary_tool",
                "summarize this document",
                true,
                false,
                toolDefinitions
        );

        assertFalse(result.success());
        assertFalse(result.shouldRecordMetrics());
        assertFalse(result.errorMessage().isBlank());
    }

    @Test
    void shouldReturnFailedResultForDisabledProvider() {
        RealLlmSelectorShadowRunner runner = runnerWithProperties(new AgentSelectorProperties());

        RealLlmSelectorShadowRunResult result = runner.run(
                "summary_tool",
                "summarize this document",
                true,
                true,
                toolDefinitions
        );

        assertFalse(result.success());
        assertFalse(result.shouldRecordMetrics());
        assertFalse(result.errorMessage().isBlank());
    }

    @Test
    void shouldReturnSuccessForFakeProviderMatch() {
        AgentSelectorProperties properties = new AgentSelectorProperties();
        properties.setLlmProvider("fake");
        RealLlmSelectorShadowRunner runner = runnerWithProperties(properties);

        RealLlmSelectorShadowRunResult result = runner.run(
                "summary_tool",
                "summarize this document",
                true,
                true,
                toolDefinitions
        );

        assertTrue(result.success());
        assertTrue(result.matched());
        assertTrue(result.shouldRecordMetrics());
        assertEquals("summary_tool", result.shadowDecision());
    }

    @Test
    void shouldReturnSuccessForFakeProviderMismatch() {
        AgentSelectorProperties properties = new AgentSelectorProperties();
        properties.setLlmProvider("fake");
        RealLlmSelectorShadowRunner runner = runnerWithProperties(properties);

        RealLlmSelectorShadowRunResult result = runner.run(
                "summary_tool",
                "answer with evidence",
                true,
                true,
                toolDefinitions
        );

        assertTrue(result.success());
        assertFalse(result.matched());
        assertTrue(result.shouldRecordMetrics());
        assertEquals("qa_tool", result.shadowDecision());
    }

    @Test
    void shouldReturnFailedResultForOpenAiCompatibleProvider() {
        AgentSelectorProperties properties = new AgentSelectorProperties();
        properties.setLlmProvider("openai_compatible");
        properties.setLlmModel("selector-model");
        RealLlmSelectorShadowRunner runner = runnerWithProperties(properties);

        RealLlmSelectorShadowRunResult result = runner.run(
                "summary_tool",
                "summarize this document",
                true,
                true,
                toolDefinitions
        );

        assertFalse(result.success());
        assertFalse(result.shouldRecordMetrics());
        assertFalse(result.errorMessage().isBlank());
    }

    private RealLlmSelectorShadowRunner runnerWithClient(LlmToolSelectionClient client) {
        return new RealLlmSelectorShadowRunner(new RealLlmToolSelector(promptBuilder, client, parser));
    }

    private RealLlmSelectorShadowRunner runnerWithProperties(AgentSelectorProperties properties) {
        return new RealLlmSelectorShadowRunner(
                new RealLlmToolSelectorFactory(
                        new LlmToolSelectionClientFactory(),
                        promptBuilder,
                        parser
                ),
                properties
        );
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
