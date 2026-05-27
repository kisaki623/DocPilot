package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;

public class LlmToolSelectionClientFactory {

    public LlmToolSelectionClient create(AgentSelectorProperties properties) {
        if (properties == null) {
            return new DisabledLlmToolSelectionClient();
        }
        String provider = properties.getLlmProvider();
        if (AgentSelectorProperties.PROVIDER_FAKE.equals(provider)) {
            return new FakeLlmToolSelectionClient();
        }
        if (AgentSelectorProperties.PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
            return new OpenAiCompatibleLlmToolSelectionClient(
                    properties.getLlmModel(),
                    properties.getLlmBaseUrl(),
                    properties.getLlmApiKey(),
                    properties.getLlmConnectTimeoutMs(),
                    properties.getLlmRequestTimeoutMs(),
                    properties.getLlmMaxTokens(),
                    properties.getLlmTemperature()
            );
        }
        return new DisabledLlmToolSelectionClient();
    }
}
