package com.docpilot.backend.ai.rag;

import java.net.http.HttpClient;

public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    private static final String DISABLED_MESSAGE =
            "OpenAI-compatible embedding model is not fully configured; no HTTP request was made.";
    private final OpenAICompatibleEmbeddingProvider provider;

    public OpenAiCompatibleEmbeddingModel(String model,
                                          String baseUrl,
                                          String apiKey,
                                          int connectTimeoutMs,
                                          int requestTimeoutMs) {
        this(model, baseUrl, apiKey, connectTimeoutMs, requestTimeoutMs, null);
    }

    OpenAiCompatibleEmbeddingModel(String model,
                                   String baseUrl,
                                   String apiKey,
                                   int connectTimeoutMs,
                                   int requestTimeoutMs,
                                   HttpClient httpClient) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setProvider(EmbeddingProperties.PROVIDER_OPENAI_COMPATIBLE);
        properties.setModel(model);
        properties.setBaseUrl(baseUrl);
        properties.setApiKey(apiKey);
        properties.setConnectTimeoutMs(normalizeConnectTimeout(connectTimeoutMs));
        properties.setRequestTimeoutMs(normalizeRequestTimeout(requestTimeoutMs));
        this.provider = httpClient == null
                ? new OpenAICompatibleEmbeddingProvider(properties)
                : new OpenAICompatibleEmbeddingProvider(properties, httpClient);
    }

    @Override
    public EmbeddingVector embed(String text) {
        try {
            return provider.embed(EmbeddingRequest.of(text)).vector();
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("not fully configured")) {
                throw new IllegalStateException(DISABLED_MESSAGE, ex);
            }
            throw ex;
        }
    }

    public OpenAiCompatibleEmbeddingRequest buildRequest(String input) {
        return provider.buildRequest(input);
    }

    public EmbeddingVector parseEmbedding(String responseBody) {
        return provider.parseEmbedding(responseBody);
    }

    public String getModel() {
        return provider.getModel();
    }

    public String getBaseUrl() {
        return provider.getBaseUrl();
    }

    public boolean hasApiKey() {
        return provider.hasApiKey();
    }

    public int getConnectTimeoutMs() {
        return provider.getConnectTimeoutMs();
    }

    public int getRequestTimeoutMs() {
        return provider.getRequestTimeoutMs();
    }

    private static int normalizeConnectTimeout(int connectTimeoutMs) {
        return connectTimeoutMs <= 0 ? 5000 : connectTimeoutMs;
    }

    private static int normalizeRequestTimeout(int requestTimeoutMs) {
        return requestTimeoutMs <= 0 ? 30000 : requestTimeoutMs;
    }
}
