package com.docpilot.backend.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class OpenAiCompatibleLlmToolSelectionClient implements LlmToolSelectionClient {

    private static final String PROVIDER = "openai_compatible";
    private static final String DISABLED_MESSAGE =
            "OpenAI-compatible LLM tool selection client is not fully configured; no HTTP request was made.";
    private static final String SYSTEM_PROMPT = """
            You are a tool selection router. Return only a valid JSON object for tool selection.
            Do not include markdown, prose, explanations, SQL, system commands, or unlisted tools.
            """;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String model;
    private final String baseUrl;
    private final String apiKey;
    private final int requestTimeoutMs;
    private final int maxTokens;
    private final double temperature;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmToolSelectionClient() {
        this("", "", "", 15000, 256, 0.0d);
    }

    public OpenAiCompatibleLlmToolSelectionClient(String model, String baseUrl, int requestTimeoutMs) {
        this(model, baseUrl, "", requestTimeoutMs, 256, 0.0d);
    }

    public OpenAiCompatibleLlmToolSelectionClient(String model,
                                                  String baseUrl,
                                                  String apiKey,
                                                  int requestTimeoutMs,
                                                  int maxTokens,
                                                  double temperature) {
        this(model, baseUrl, apiKey, requestTimeoutMs, maxTokens, temperature,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(normalizeTimeout(requestTimeoutMs)))
                        .build());
    }

    OpenAiCompatibleLlmToolSelectionClient(String model,
                                           String baseUrl,
                                           String apiKey,
                                           int requestTimeoutMs,
                                           int maxTokens,
                                           double temperature,
                                           HttpClient httpClient) {
        this.model = model == null ? "" : model.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.requestTimeoutMs = normalizeTimeout(requestTimeoutMs);
        this.maxTokens = maxTokens <= 0 ? 256 : maxTokens;
        this.temperature = temperature < 0.0d ? 0.0d : temperature;
        this.httpClient = httpClient;
    }

    @Override
    public LlmToolSelectionClientResponse completeSelectionPrompt(String prompt) {
        if (!isConfigured()) {
            return disabled(DISABLED_MESSAGE);
        }
        try {
            long startedAt = System.nanoTime();
            HttpResponse<String> response = httpClient.send(
                    buildHttpRequest(prompt),
                    HttpResponse.BodyHandlers.ofString()
            );
            long latencyMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return disabled("provider_http_status=" + response.statusCode() + ", latencyMs=" + latencyMs);
            }
            String content = extractContent(response.body());
            if (content.isBlank()) {
                return disabled("provider_empty_content, latencyMs=" + latencyMs);
            }
            return new LlmToolSelectionClientResponse(content, PROVIDER, model, false, "");
        } catch (IOException ex) {
            return disabled("provider_io_error=" + safeErrorType(ex));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return disabled("provider_interrupted");
        } catch (IllegalArgumentException ex) {
            return disabled("provider_invalid_response=" + safeErrorType(ex));
        }
    }

    public OpenAiCompatibleToolSelectionRequest buildRequest(String prompt) {
        return new OpenAiCompatibleToolSelectionRequest(
                model,
                List.of(
                        new OpenAiCompatibleToolSelectionRequest.Message("system", SYSTEM_PROMPT.strip()),
                        new OpenAiCompatibleToolSelectionRequest.Message("user", prompt == null ? "" : prompt)
                ),
                temperature,
                maxTokens,
                false
        );
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

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    private boolean isConfigured() {
        return !apiKey.isBlank() && !baseUrl.isBlank() && !model.isBlank();
    }

    private HttpRequest buildHttpRequest(String prompt) throws IOException {
        String requestBody = OBJECT_MAPPER.writeValueAsString(toRequestBody(buildRequest(prompt)));
        return HttpRequest.newBuilder()
                .uri(URI.create(completionsUrl()))
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }

    private Map<String, Object> toRequestBody(OpenAiCompatibleToolSelectionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("messages", request.messages().stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList());
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        body.put("stream", request.stream());
        return body;
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody == null ? "" : responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                return "";
            }
            return content.asText("");
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to parse provider response JSON", ex);
        }
    }

    private String completionsUrl() {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + "/chat/completions";
    }

    private LlmToolSelectionClientResponse disabled(String errorMessage) {
        return new LlmToolSelectionClientResponse("", PROVIDER, model, true, errorMessage);
    }

    private static int normalizeTimeout(int requestTimeoutMs) {
        return requestTimeoutMs <= 0 ? 15000 : requestTimeoutMs;
    }

    private String safeErrorType(Exception ex) {
        if (ex instanceof java.net.http.HttpTimeoutException || ex.getCause() instanceof TimeoutException) {
            return "timeout";
        }
        return ex.getClass().getSimpleName();
    }
}
