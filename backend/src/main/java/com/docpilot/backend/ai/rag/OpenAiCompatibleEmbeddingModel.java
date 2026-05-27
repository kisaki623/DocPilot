package com.docpilot.backend.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    private static final String DISABLED_MESSAGE =
            "OpenAI-compatible embedding model is not fully configured; no HTTP request was made.";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String model;
    private final String baseUrl;
    private final String apiKey;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final HttpClient httpClient;

    public OpenAiCompatibleEmbeddingModel(String model,
                                          String baseUrl,
                                          String apiKey,
                                          int connectTimeoutMs,
                                          int requestTimeoutMs) {
        this(model, baseUrl, apiKey, connectTimeoutMs, requestTimeoutMs,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(normalizeConnectTimeout(connectTimeoutMs)))
                        .build());
    }

    OpenAiCompatibleEmbeddingModel(String model,
                                   String baseUrl,
                                   String apiKey,
                                   int connectTimeoutMs,
                                   int requestTimeoutMs,
                                   HttpClient httpClient) {
        this.model = model == null ? "" : model.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.connectTimeoutMs = normalizeConnectTimeout(connectTimeoutMs);
        this.requestTimeoutMs = normalizeRequestTimeout(requestTimeoutMs);
        this.httpClient = httpClient;
    }

    @Override
    public EmbeddingVector embed(String text) {
        if (!isConfigured()) {
            throw new IllegalStateException(DISABLED_MESSAGE);
        }
        if (text == null) {
            text = "";
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    buildHttpRequest(text),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Embedding provider returned status " + response.statusCode());
            }
            return parseEmbedding(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("Embedding provider I/O error: " + safeErrorType(ex), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding provider request was interrupted", ex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Embedding provider returned invalid response: "
                    + ex.getClass().getSimpleName(), ex);
        }
    }

    public OpenAiCompatibleEmbeddingRequest buildRequest(String input) {
        return new OpenAiCompatibleEmbeddingRequest(model, input == null ? "" : input);
    }

    public EmbeddingVector parseEmbedding(String responseBody) {
        try {
            JsonNode values = OBJECT_MAPPER.readTree(responseBody == null ? "" : responseBody)
                    .path("data")
                    .path(0)
                    .path("embedding");
            if (!values.isArray() || values.isEmpty()) {
                throw new IllegalArgumentException("embedding array is empty");
            }
            List<Double> embeddingValues = new ArrayList<>();
            for (JsonNode value : values) {
                if (!value.isNumber()) {
                    throw new IllegalArgumentException("embedding value is not numeric");
                }
                embeddingValues.add(value.asDouble());
            }
            return new EmbeddingVector(embeddingValues);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to parse embedding provider response JSON", ex);
        }
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    private boolean isConfigured() {
        return !apiKey.isBlank() && !baseUrl.isBlank() && !model.isBlank();
    }

    private HttpRequest buildHttpRequest(String input) throws IOException {
        String requestBody = OBJECT_MAPPER.writeValueAsString(toRequestBody(buildRequest(input)));
        return HttpRequest.newBuilder()
                .uri(URI.create(embeddingsUrl()))
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }

    private Map<String, Object> toRequestBody(OpenAiCompatibleEmbeddingRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("input", request.input());
        return body;
    }

    private String embeddingsUrl() {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + "/embeddings";
    }

    private static int normalizeConnectTimeout(int connectTimeoutMs) {
        return connectTimeoutMs <= 0 ? 5000 : connectTimeoutMs;
    }

    private static int normalizeRequestTimeout(int requestTimeoutMs) {
        return requestTimeoutMs <= 0 ? 30000 : requestTimeoutMs;
    }

    private String safeErrorType(Exception ex) {
        if (ex instanceof java.net.http.HttpTimeoutException || ex.getCause() instanceof TimeoutException) {
            return "timeout";
        }
        return ex.getClass().getSimpleName();
    }
}
