package com.docpilot.backend.ai.agent.tool;

import java.util.List;

public class OpenAiCompatibleLlmToolSelectionClient implements LlmToolSelectionClient {

    private static final String PROVIDER = "openai_compatible";
    private static final String DISABLED_MESSAGE =
            "OpenAI-compatible LLM tool selection client is disabled; no HTTP request was made.";

    private final String model;
    private final String baseUrl;
    private final int requestTimeoutMs;

    public OpenAiCompatibleLlmToolSelectionClient() {
        this("", "", 3000);
    }

    public OpenAiCompatibleLlmToolSelectionClient(String model, String baseUrl, int requestTimeoutMs) {
        this.model = model == null ? "" : model.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.requestTimeoutMs = requestTimeoutMs <= 0 ? 3000 : requestTimeoutMs;
    }

    @Override
    public LlmToolSelectionClientResponse completeSelectionPrompt(String prompt) {
        return new LlmToolSelectionClientResponse(
                "",
                PROVIDER,
                model,
                true,
                DISABLED_MESSAGE
        );
    }

    public OpenAiCompatibleToolSelectionRequest buildRequest(String prompt) {
        return new OpenAiCompatibleToolSelectionRequest(
                model,
                List.of(new OpenAiCompatibleToolSelectionRequest.Message("user", prompt == null ? "" : prompt)),
                0.0d,
                512
        );
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }
}
