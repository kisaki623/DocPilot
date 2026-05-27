package com.docpilot.backend.ai.agent.tool;

import java.util.List;

public class RealLlmToolSelector implements LlmToolSelector {

    private final LlmToolSelectionPromptBuilder promptBuilder;
    private final LlmToolSelectionClient client;
    private final LlmToolSelectionParser parser;

    public RealLlmToolSelector(LlmToolSelectionPromptBuilder promptBuilder,
                               LlmToolSelectionClient client,
                               LlmToolSelectionParser parser) {
        this.promptBuilder = promptBuilder;
        this.client = client;
        this.parser = parser;
    }

    @Override
    public LlmToolSelectionResult selectWithPrompt(String task,
                                                   boolean parseReady,
                                                   boolean hasSummary,
                                                   List<ToolDefinition> toolDefinitions) {
        String prompt = promptBuilder.build(task, parseReady, hasSummary, toolDefinitions);
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt(prompt);
        if (response.disabled()) {
            throw new IllegalStateException("LLM tool selection client is disabled: " + response.errorMessage());
        }
        if (response.rawText().isBlank()) {
            throw new IllegalArgumentException("LLM tool selection client returned blank response");
        }
        return parser.parse(response.rawText());
    }
}
