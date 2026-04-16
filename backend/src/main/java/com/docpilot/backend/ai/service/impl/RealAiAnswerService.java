package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.exception.AiNonRetryableException;
import com.docpilot.backend.ai.exception.AiRetryableException;
import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.common.metrics.DocPilotMetrics;
import com.docpilot.backend.common.util.ValidationUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Consumer;

@Service
@ConditionalOnProperty(prefix = "app.ai", name = "mode", havingValue = "real")
public class RealAiAnswerService implements AiAnswerService {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 2_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 8_000;
    private static final double DEFAULT_TEMPERATURE = 0.2D;
    private static final int ERROR_SUMMARY_MAX_CHARS = 160;
    private static final String PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";
    private static final String PROVIDER_SILICONFLOW = "siliconflow";
    private static final String SILICONFLOW_BASE_URL_HINT = "https://api.siliconflow.cn/v1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai.real.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${app.ai.real.provider:openai-compatible}")
    private String provider;

    @Value("${app.ai.real.api-key:}")
    private String apiKey;

    @Value("${app.ai.real.model:gpt-4o-mini}")
    private String model;

    @Value("${app.ai.real.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${app.ai.real.read-timeout-ms:8000}")
    private int readTimeoutMs;

    @Value("${app.ai.real.temperature:0.2}")
    private double temperature;

    @Value("${app.ai.real.max-output-tokens:800}")
    private int maxOutputTokens;

    @Value("${app.ai.real.cost.input-per-1k-usd:0}")
    private double inputCostPer1kUsd;

    @Value("${app.ai.real.cost.output-per-1k-usd:0}")
    private double outputCostPer1kUsd;

    @PostConstruct
    void validateOnStartup() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("app.ai.mode=real 时必须配置 AI_REAL_API_KEY");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("app.ai.mode=real 时必须配置 AI_REAL_MODEL");
        }

        String normalizedProvider = resolveNormalizedProviderForStartup();
        String normalizedBaseUrl = resolveNormalizedBaseUrlForStartup();
        validateProviderBaseUrlCompatibilityForStartup(normalizedProvider, normalizedBaseUrl);

        String url = normalizedBaseUrl + "/chat/completions";
        try {
            URI.create(url);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("app.ai.mode=real 时 AI_REAL_BASE_URL 配置非法: " + url, ex);
        }
    }

    @Override
    public String answer(String documentContext, String question) {
        ValidationUtils.requireNonBlank(documentContext, "documentContext");
        ValidationUtils.requireNonBlank(question, "question");
        validateConfig();

        String requestBody = buildRequestBody(documentContext, question, false);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveChatCompletionsUrl()))
                .timeout(Duration.ofMillis(resolveReadTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = createHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return handleResponse(documentContext, question, response);
        } catch (HttpTimeoutException | ConnectException timeoutEx) {
            throw new AiRetryableException("real model call timeout or connection failure", timeoutEx);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AiNonRetryableException("real model call was interrupted", interruptedException);
        } catch (IOException ioException) {
            throw new AiRetryableException("real model network error", ioException);
        }
    }

    @Override
    public void streamAnswer(String documentContext, String question, Consumer<String> chunkConsumer) {
        ValidationUtils.requireNonBlank(documentContext, "documentContext");
        ValidationUtils.requireNonBlank(question, "question");
        ValidationUtils.requireNonNull(chunkConsumer, "chunkConsumer");
        validateConfig();

        String requestBody = buildRequestBody(documentContext, question, true);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveChatCompletionsUrl()))
                .timeout(Duration.ofMillis(resolveReadTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<InputStream> response = createHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = response.statusCode();
            if (statusCode >= 400) {
                try (InputStream errorBodyStream = response.body()) {
                    String errorBody = new String(errorBodyStream.readAllBytes(), StandardCharsets.UTF_8);
                    throwStreamStatusException(statusCode, errorBody);
                }
                return;
            }

            StringBuilder fullAnswerBuilder = new StringBuilder();
            JsonNode usageNode = null;
            try (InputStream bodyStream = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
                String line;
                boolean done = false;

                while (!done && (line = reader.readLine()) != null) {
                    if (line == null || line.isBlank() || line.startsWith(":")) {
                        continue;
                    }
                    if (!line.startsWith("data:")) {
                        continue;
                    }

                    String dataLine = line.substring("data:".length());
                    if (dataLine.startsWith(" ")) {
                        dataLine = dataLine.substring(1);
                    }

                    StreamChunkEvent event = parseSseDataEvent(dataLine);
                    if (event.doneFlag()) {
                        done = true;
                        continue;
                    }
                    if (!event.chunk().isEmpty()) {
                        chunkConsumer.accept(event.chunk());
                        fullAnswerBuilder.append(event.chunk());
                    }
                    if (event.usageNode() != null) {
                        usageNode = event.usageNode();
                    }
                }
            }

            String fullAnswer = fullAnswerBuilder.toString();
            if (fullAnswer.isBlank()) {
                throw new AiNonRetryableException("real model returned empty stream answer");
            }

            TokenUsage usage = resolveTokenUsage(
                    usageNode == null ? objectMapper.createObjectNode() : usageNode,
                    documentContext,
                    question,
                    fullAnswer
            );
            recordCostMetrics(usage);
        } catch (HttpTimeoutException | ConnectException timeoutEx) {
            throw new AiRetryableException("real model stream timeout or connection failure", timeoutEx);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AiNonRetryableException("real model stream was interrupted", interruptedException);
        } catch (IOException ioException) {
            throw new AiRetryableException("real model stream network error", ioException);
        }
    }

    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(resolveConnectTimeoutMs()))
                .build();
    }

    private String handleResponse(String documentContext, String question, HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();
        String errorSummary = extractErrorSummary(body);

        if (statusCode == 401 || statusCode == 403) {
            throw new AiNonRetryableException("real model auth failed（鉴权失败）, status=" + statusCode
                    + ", please check AI_REAL_API_KEY / AI_REAL_BASE_URL, error=" + errorSummary);
        }
        if (statusCode == 429) {
            throw new AiRetryableException("real model rate limited, status=429, error=" + errorSummary);
        }
        if (statusCode == 408 || statusCode >= 500) {
            throw new AiRetryableException("real model service temporarily unavailable, status=" + statusCode + ", error=" + errorSummary);
        }
        if (statusCode >= 400) {
            throw new AiNonRetryableException("real model request is non-retryable, status=" + statusCode + ", error=" + errorSummary);
        }

        try {
            JsonNode rootNode = objectMapper.readTree(body);
            JsonNode messageNode = rootNode.path("choices").path(0).path("message").path("content");
            String answer = messageNode.isMissingNode() ? null : messageNode.asText();
            if (answer == null || answer.isBlank()) {
                throw new AiNonRetryableException("real model returned empty answer");
            }

            TokenUsage usage = resolveTokenUsage(rootNode.path("usage"), documentContext, question, answer);
            recordCostMetrics(usage);
            return answer.trim();
        } catch (AiNonRetryableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiNonRetryableException("failed to parse model response", ex);
        }
    }

    private void throwStreamStatusException(int statusCode, String responseBody) {
        String errorSummary = extractErrorSummary(responseBody);
        if (statusCode == 401 || statusCode == 403) {
            throw new AiNonRetryableException("real model auth failed（鉴权失败）, status=" + statusCode + ", error=" + errorSummary);
        }
        if (statusCode == 429) {
            throw new AiRetryableException("real model rate limited, status=429, error=" + errorSummary);
        }
        if (statusCode == 408 || statusCode >= 500) {
            throw new AiRetryableException("real model service temporarily unavailable, status=" + statusCode + ", error=" + errorSummary);
        }
        throw new AiNonRetryableException("real model request is non-retryable, status=" + statusCode + ", error=" + errorSummary);
    }

    private StreamChunkEvent parseSseDataEvent(String dataPayload) {
        if (dataPayload == null || dataPayload.isBlank()) {
            return StreamChunkEvent.emptyEvent();
        }

        String normalizedPayload = dataPayload.trim();
        if ("[DONE]".equals(normalizedPayload)) {
            return StreamChunkEvent.doneEvent();
        }

        try {
            JsonNode rootNode = objectMapper.readTree(normalizedPayload);
            JsonNode usageNode = rootNode.path("usage");
            if (usageNode.isMissingNode() || usageNode.isNull()) {
                usageNode = null;
            }
            String chunk = extractStreamChunk(rootNode);
            return new StreamChunkEvent(chunk, usageNode, false);
        } catch (Exception ex) {
            throw new AiNonRetryableException("failed to parse stream response", ex);
        }
    }

    private String extractStreamChunk(JsonNode rootNode) {
        JsonNode choicesNode = rootNode.path("choices");
        if (!choicesNode.isArray() || choicesNode.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode choiceNode : choicesNode) {
            String chunk = extractChunkTextFromChoice(choiceNode);
            if (!chunk.isEmpty()) {
                builder.append(chunk);
            }
        }
        return builder.toString();
    }

    private String extractChunkTextFromChoice(JsonNode choiceNode) {
        String chunk = extractTextFromNode(choiceNode.path("delta").path("content"));
        if (!chunk.isEmpty()) {
            return chunk;
        }
        chunk = extractTextFromNode(choiceNode.path("text"));
        if (!chunk.isEmpty()) {
            return chunk;
        }
        return extractTextFromNode(choiceNode.path("message").path("content"));
    }

    private String extractTextFromNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    builder.append(item.asText());
                    continue;
                }
                JsonNode textNode = item.path("text");
                if (textNode.isTextual()) {
                    builder.append(textNode.asText());
                }
            }
            return builder.toString();
        }
        if (node.isObject()) {
            JsonNode textNode = node.path("text");
            if (textNode.isTextual()) {
                return textNode.asText();
            }
        }
        return "";
    }

    private void recordCostMetrics(TokenUsage usage) {
        DocPilotMetrics.recordAiTokenUsage("qa.model", "prompt", usage.promptTokens());
        DocPilotMetrics.recordAiTokenUsage("qa.model", "completion", usage.completionTokens());
        DocPilotMetrics.recordAiTokenUsage("qa.model", "total", usage.totalTokens());

        double estimatedCostUsd = usage.promptTokens() * (inputCostPer1kUsd / 1000.0D)
                + usage.completionTokens() * (outputCostPer1kUsd / 1000.0D);
        if (estimatedCostUsd > 0.0D) {
            DocPilotMetrics.recordAiCost("qa.model", "usd", estimatedCostUsd);
        }
    }

    private TokenUsage resolveTokenUsage(JsonNode usageNode, String documentContext, String question, String answer) {
        int promptTokens = usageNode.path("prompt_tokens").asInt(-1);
        int completionTokens = usageNode.path("completion_tokens").asInt(-1);
        int totalTokens = usageNode.path("total_tokens").asInt(-1);

        if (promptTokens < 0) {
            promptTokens = estimateTokens(documentContext) + estimateTokens(question);
        }
        if (completionTokens < 0) {
            completionTokens = estimateTokens(answer);
        }
        if (totalTokens < 0) {
            totalTokens = promptTokens + completionTokens;
        }

        return new TokenUsage(promptTokens, completionTokens, totalTokens);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (text.length() + 3) / 4);
    }

    private String buildRequestBody(String documentContext, String question, boolean stream) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", resolveTemperature());
        root.put("max_tokens", resolveMaxOutputTokens());
        root.put("stream", stream);

        ArrayNode messages = root.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", "You are DocPilot QA assistant. Answer only based on provided document content. If uncertain, clearly say so. Use concise Chinese.");
        messages.addObject()
                .put("role", "user")
                .put("content", "文档内容:\n" + documentContext + "\n\n问题:\n" + question);
        return root.toString();
    }

    private String resolveChatCompletionsUrl() {
        String normalizedBaseUrl = resolveNormalizedBaseUrlForRuntime();
        return normalizedBaseUrl + "/chat/completions";
    }

    private int resolveConnectTimeoutMs() {
        return connectTimeoutMs > 0 ? connectTimeoutMs : DEFAULT_CONNECT_TIMEOUT_MS;
    }

    private int resolveReadTimeoutMs() {
        return readTimeoutMs > 0 ? readTimeoutMs : DEFAULT_READ_TIMEOUT_MS;
    }

    private int resolveMaxOutputTokens() {
        return Math.max(1, maxOutputTokens);
    }

    private double resolveTemperature() {
        if (temperature < 0.0D || temperature > 2.0D) {
            return DEFAULT_TEMPERATURE;
        }
        return temperature;
    }

    private void validateConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiNonRetryableException("real model apiKey is not configured");
        }
        if (model == null || model.isBlank()) {
            throw new AiNonRetryableException("real model model is not configured");
        }

        String normalizedProvider = resolveNormalizedProviderForRuntime();
        String normalizedBaseUrl = resolveNormalizedBaseUrlForRuntime();
        validateProviderBaseUrlCompatibilityForRuntime(normalizedProvider, normalizedBaseUrl);
    }

    private String resolveNormalizedProviderForStartup() {
        String normalizedProvider = normalizeProvider();
        if (normalizedProvider.equals(PROVIDER_OPENAI_COMPATIBLE) || normalizedProvider.equals(PROVIDER_SILICONFLOW)) {
            return normalizedProvider;
        }
        throw new IllegalStateException("app.ai.mode=real 时 AI_REAL_PROVIDER 仅支持 "
                + PROVIDER_OPENAI_COMPATIBLE + "/" + PROVIDER_SILICONFLOW + "，当前值=" + provider);
    }

    private String resolveNormalizedProviderForRuntime() {
        String normalizedProvider = normalizeProvider();
        if (normalizedProvider.equals(PROVIDER_OPENAI_COMPATIBLE) || normalizedProvider.equals(PROVIDER_SILICONFLOW)) {
            return normalizedProvider;
        }
        throw new AiNonRetryableException("real model provider is invalid, AI_REAL_PROVIDER only supports "
                + PROVIDER_OPENAI_COMPATIBLE + "/" + PROVIDER_SILICONFLOW);
    }

    private String normalizeProvider() {
        if (provider == null || provider.isBlank()) {
            return PROVIDER_OPENAI_COMPATIBLE;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveNormalizedBaseUrlForStartup() {
        String normalizedBaseUrl = normalizeBaseUrl();
        if (normalizedBaseUrl.isBlank()) {
            throw new IllegalStateException("app.ai.mode=real 时必须配置 AI_REAL_BASE_URL");
        }
        return normalizedBaseUrl;
    }

    private String resolveNormalizedBaseUrlForRuntime() {
        String normalizedBaseUrl = normalizeBaseUrl();
        if (normalizedBaseUrl.isBlank()) {
            throw new AiNonRetryableException("real model baseUrl is not configured");
        }
        return normalizedBaseUrl;
    }

    private String normalizeBaseUrl() {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl;
    }

    private void validateProviderBaseUrlCompatibilityForStartup(String normalizedProvider, String normalizedBaseUrl) {
        if (!PROVIDER_SILICONFLOW.equals(normalizedProvider)) {
            return;
        }
        if (!isSiliconFlowBaseUrl(normalizedBaseUrl)) {
            throw new IllegalStateException("AI_REAL_PROVIDER=siliconflow 时，AI_REAL_BASE_URL 必须指向硅基流动 OpenAI 兼容网关，推荐值: "
                    + SILICONFLOW_BASE_URL_HINT);
        }
    }

    private void validateProviderBaseUrlCompatibilityForRuntime(String normalizedProvider, String normalizedBaseUrl) {
        if (!PROVIDER_SILICONFLOW.equals(normalizedProvider)) {
            return;
        }
        if (!isSiliconFlowBaseUrl(normalizedBaseUrl)) {
            throw new AiNonRetryableException("AI_REAL_PROVIDER=siliconflow 时，AI_REAL_BASE_URL 必须指向硅基流动网关，推荐值: "
                    + SILICONFLOW_BASE_URL_HINT);
        }
    }

    private boolean isSiliconFlowBaseUrl(String normalizedBaseUrl) {
        try {
            URI uri = URI.create(normalizedBaseUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            return host != null
                    && "api.siliconflow.cn".equalsIgnoreCase(host)
                    && path != null
                    && path.startsWith("/v1");
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String extractErrorSummary(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "empty-body";
        }

        String summary = null;
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode errorNode = rootNode.path("error");
            if (errorNode.isTextual()) {
                summary = errorNode.asText();
            } else if (errorNode.isObject()) {
                JsonNode messageNode = errorNode.path("message");
                if (messageNode.isTextual()) {
                    summary = messageNode.asText();
                } else {
                    summary = errorNode.toString();
                }
            }
        } catch (Exception ignored) {
            // Ignore parse failure and fallback to raw body.
        }

        if (summary == null || summary.isBlank()) {
            summary = responseBody;
        }

        summary = summary.replaceAll("\\s+", " ").trim();
        if (summary.length() <= ERROR_SUMMARY_MAX_CHARS) {
            return summary;
        }
        return summary.substring(0, ERROR_SUMMARY_MAX_CHARS) + "...";
    }

    private record StreamChunkEvent(String chunk, JsonNode usageNode, boolean doneFlag) {
        private static StreamChunkEvent emptyEvent() {
            return new StreamChunkEvent("", null, false);
        }

        private static StreamChunkEvent doneEvent() {
            return new StreamChunkEvent("", null, true);
        }
    }

    private record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
    }
}
