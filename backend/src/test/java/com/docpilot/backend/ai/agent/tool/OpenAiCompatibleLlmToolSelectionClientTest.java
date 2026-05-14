package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleLlmToolSelectionClientTest {

    @Test
    void shouldBuildRequestWithPrompt() {
        OpenAiCompatibleLlmToolSelectionClient client = new OpenAiCompatibleLlmToolSelectionClient(
                "selector-model",
                "https://example.invalid/v1",
                5000
        );

        OpenAiCompatibleToolSelectionRequest request = client.buildRequest("select a tool");

        assertThat(request.model()).isEqualTo("selector-model");
        assertThat(request.temperature()).isEqualTo(0.0d);
        assertThat(request.maxTokens()).isEqualTo(512);
        assertThat(request.messages()).hasSize(1);
        assertThat(request.messages().get(0).role()).isEqualTo("user");
        assertThat(request.messages().get(0).content()).isEqualTo("select a tool");
        assertThat(client.getBaseUrl()).isEqualTo("https://example.invalid/v1");
        assertThat(client.getRequestTimeoutMs()).isEqualTo(5000);
    }

    @Test
    void shouldReturnDisabledResponseWithoutNetworkCall() {
        OpenAiCompatibleLlmToolSelectionClient client = new OpenAiCompatibleLlmToolSelectionClient(
                "selector-model",
                "https://example.invalid/v1",
                5000
        );

        LlmToolSelectionClientResponse response = client.completeSelectionPrompt("select a tool");

        assertThat(response.provider()).isEqualTo("openai_compatible");
        assertThat(response.model()).isEqualTo("selector-model");
        assertThat(response.disabled()).isTrue();
        assertThat(response.rawText()).isEmpty();
        assertThat(response.errorMessage()).contains("disabled");
    }

    @Test
    void shouldRemainDisabledForBlankPrompt() {
        OpenAiCompatibleLlmToolSelectionClient client = new OpenAiCompatibleLlmToolSelectionClient(
                "selector-model",
                "",
                0
        );

        OpenAiCompatibleToolSelectionRequest request = client.buildRequest("");
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt("");

        assertThat(request.messages().get(0).content()).isEmpty();
        assertThat(client.getRequestTimeoutMs()).isEqualTo(3000);
        assertThat(response.provider()).isEqualTo("openai_compatible");
        assertThat(response.disabled()).isTrue();
    }
}
