package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmToolSelectionClientFactoryTest {

    private final LlmToolSelectionClientFactory factory = new LlmToolSelectionClientFactory();

    @Test
    void shouldReturnDisabledClientByDefault() {
        LlmToolSelectionClient client = factory.create(new AgentSelectorProperties());

        LlmToolSelectionClientResponse response = client.completeSelectionPrompt("select");

        assertThat(client).isInstanceOf(DisabledLlmToolSelectionClient.class);
        assertThat(response.disabled()).isTrue();
        assertThat(response.provider()).isEqualTo("disabled");
    }

    @Test
    void shouldReturnFakeClientForFakeProvider() {
        AgentSelectorProperties properties = new AgentSelectorProperties();
        properties.setLlmProvider("fake");

        LlmToolSelectionClient client = factory.create(properties);
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt("Current task: summarize this document");

        assertThat(client).isInstanceOf(FakeLlmToolSelectionClient.class);
        assertThat(response.disabled()).isFalse();
        assertThat(response.provider()).isEqualTo("fake");
        assertThat(response.model()).isEqualTo("fake-selector");
    }

    @Test
    void shouldReturnOpenAiCompatibleSkeletonForProvider() {
        AgentSelectorProperties properties = new AgentSelectorProperties();
        properties.setLlmProvider("openai_compatible");
        properties.setLlmModel("selector-model");
        properties.setLlmBaseUrl("https://example.invalid/v1");
        properties.setLlmRequestTimeoutMs(5000);
        properties.setLlmMaxTokens(128);
        properties.setLlmTemperature(0.1d);

        LlmToolSelectionClient client = factory.create(properties);
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt("select");

        assertThat(client).isInstanceOf(OpenAiCompatibleLlmToolSelectionClient.class);
        assertThat(response.disabled()).isTrue();
        assertThat(response.provider()).isEqualTo("openai_compatible");
        assertThat(response.model()).isEqualTo("selector-model");
        assertThat(((OpenAiCompatibleLlmToolSelectionClient) client).getMaxTokens()).isEqualTo(128);
        assertThat(((OpenAiCompatibleLlmToolSelectionClient) client).getTemperature()).isEqualTo(0.1d);
    }

    @Test
    void shouldFallbackDisabledForUnknownProvider() {
        AgentSelectorProperties properties = new AgentSelectorProperties() {
            @Override
            public String getLlmProvider() {
                return "unknown";
            }
        };

        LlmToolSelectionClient client = factory.create(properties);
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt("select");

        assertThat(client).isInstanceOf(DisabledLlmToolSelectionClient.class);
        assertThat(response.disabled()).isTrue();
        assertThat(response.provider()).isEqualTo("disabled");
    }

    @Test
    void shouldFallbackDisabledForNullProperties() {
        LlmToolSelectionClient client = factory.create(null);
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt("");

        assertThat(client).isInstanceOf(DisabledLlmToolSelectionClient.class);
        assertThat(response.disabled()).isTrue();
    }
}
