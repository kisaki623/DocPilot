package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;

public class RealLlmToolSelectorFactory {

    private final LlmToolSelectionClientFactory clientFactory;
    private final LlmToolSelectionPromptBuilder promptBuilder;
    private final LlmToolSelectionParser parser;

    public RealLlmToolSelectorFactory(LlmToolSelectionClientFactory clientFactory,
                                      LlmToolSelectionPromptBuilder promptBuilder,
                                      LlmToolSelectionParser parser) {
        this.clientFactory = clientFactory;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
    }

    public RealLlmToolSelector create(AgentSelectorProperties properties) {
        LlmToolSelectionClient client = clientFactory.create(properties);
        return new RealLlmToolSelector(promptBuilder, client, parser);
    }
}
