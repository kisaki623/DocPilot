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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class OpenAICompatibleEmbeddingProvider implements EmbeddingProvider {

    public static final String PROVIDER = "openai_compatible";
    private static final String DISABLED_MESSAGE =
            "OpenAI-compatible embedding provider is not fully configured; no HTTP request was made.";
    private static final int MAX_BATCH_SIZE = 10;
    private static final int ERROR_DETAIL_MAX_LENGTH = 240;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EmbeddingProperties properties;
    private final HttpClient httpClient;

    public OpenAICompatibleEmbeddingProvider(EmbeddingProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(resolveProperties(properties).getConnectTimeoutMs()))
                .build());
    }

    OpenAICompatibleEmbeddingProvider(EmbeddingProperties properties, HttpClient httpClient) {
        this.properties = resolveProperties(properties);
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
    }

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        List<EmbeddingResult> results = embedBatch(List.of(request == null ? EmbeddingRequest.of("") : request));
        if (results.isEmpty()) {
            throw new IllegalStateException("Embedding provider returned no embeddings");
        }
        return results.get(0);
    }

    @Override
    public List<EmbeddingResult> embedBatch(List<EmbeddingRequest> requests) {
        if (!isConfigured()) {
            throw new IllegalStateException(DISABLED_MESSAGE);
        }
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<EmbeddingRequest> resolvedRequests = requests.stream()
                .map(request -> request == null ? EmbeddingRequest.of("") : request)
                .toList();
        try {
            List<EmbeddingResult> results = new ArrayList<>();
            for (int start = 0; start < resolvedRequests.size(); start += MAX_BATCH_SIZE) {
                int end = Math.min(start + MAX_BATCH_SIZE, resolvedRequests.size());
                results.addAll(sendEmbeddingBatch(resolvedRequests.subList(start, end)));
            }
            return results;
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

    private List<EmbeddingResult> sendEmbeddingBatch(List<EmbeddingRequest> requests)
            throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                buildHttpRequest(requests),
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Embedding provider returned status " + response.statusCode()
                    + providerErrorSuffix(response.body()));
        }
        return parseEmbeddingResults(response.body(), requests);
    }

    public OpenAiCompatibleEmbeddingRequest buildRequest(String input) {
        return new OpenAiCompatibleEmbeddingRequest(properties.getModel(), input == null ? "" : input);
    }

    public Map<String, Object> buildBatchRequestBody(List<EmbeddingRequest> requests) {
        List<EmbeddingRequest> resolvedRequests = requests == null ? List.of() : requests.stream()
                .map(request -> request == null ? EmbeddingRequest.of("") : request)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        if (resolvedRequests.size() == 1) {
            body.put("input", resolvedRequests.get(0).input());
        } else {
            body.put("input", resolvedRequests.stream()
                    .map(EmbeddingRequest::input)
                    .toList());
        }
        return body;
    }

    public EmbeddingVector parseEmbedding(String responseBody) {
        return parseEmbeddingResults(responseBody, List.of(EmbeddingRequest.of(""))).get(0).vector();
    }

    public List<EmbeddingResult> parseEmbeddingResults(String responseBody, List<EmbeddingRequest> requests) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody == null ? "" : responseBody);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new IllegalArgumentException("embedding data array is empty");
            }
            List<EmbeddingRequest> resolvedRequests = requests == null ? List.of() : requests;
            String responseModel = root.path("model").asText(properties.getModel());
            List<IndexedEmbedding> indexedEmbeddings = new ArrayList<>();
            int fallbackIndex = 0;
            for (JsonNode item : data) {
                JsonNode values = item.path("embedding");
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
                int index = item.has("index") && item.path("index").canConvertToInt()
                        ? item.path("index").asInt()
                        : fallbackIndex;
                indexedEmbeddings.add(new IndexedEmbedding(index, new EmbeddingVector(embeddingValues)));
                fallbackIndex++;
            }

            return indexedEmbeddings.stream()
                    .sorted(Comparator.comparingInt(IndexedEmbedding::index))
                    .map(indexed -> toResult(indexed, resolvedRequests, responseModel))
                    .toList();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to parse embedding provider response JSON", ex);
        }
    }

    public String getModel() {
        return properties.getModel();
    }

    public String getBaseUrl() {
        return properties.getBaseUrl();
    }

    public boolean hasApiKey() {
        return !properties.getApiKey().isBlank();
    }

    public int getConnectTimeoutMs() {
        return properties.getConnectTimeoutMs();
    }

    public int getRequestTimeoutMs() {
        return properties.getRequestTimeoutMs();
    }

    private EmbeddingResult toResult(IndexedEmbedding indexedEmbedding,
                                     List<EmbeddingRequest> requests,
                                     String responseModel) {
        EmbeddingRequest request = indexedEmbedding.index() >= 0 && indexedEmbedding.index() < requests.size()
                ? requests.get(indexedEmbedding.index())
                : EmbeddingRequest.of("");
        String model = responseModel == null || responseModel.isBlank()
                ? properties.getModel()
                : responseModel.trim();
        return new EmbeddingResult(
                indexedEmbedding.vector(),
                PROVIDER,
                model,
                indexedEmbedding.vector().dimension(),
                request.metadata()
        );
    }

    private boolean isConfigured() {
        return properties.isEnabled()
                && !properties.getApiKey().isBlank()
                && !properties.getBaseUrl().isBlank()
                && !properties.getModel().isBlank();
    }

    private HttpRequest buildHttpRequest(List<EmbeddingRequest> requests) throws IOException {
        String requestBody = OBJECT_MAPPER.writeValueAsString(buildBatchRequestBody(requests));
        return HttpRequest.newBuilder()
                .uri(URI.create(embeddingsUrl()))
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }

    private String embeddingsUrl() {
        String normalizedBaseUrl = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        return normalizedBaseUrl + "/embeddings";
    }

    private static EmbeddingProperties resolveProperties(EmbeddingProperties properties) {
        return properties == null ? new EmbeddingProperties() : properties;
    }

    private String safeErrorType(Exception ex) {
        if (ex instanceof java.net.http.HttpTimeoutException || ex.getCause() instanceof TimeoutException) {
            return "timeout";
        }
        return ex.getClass().getSimpleName();
    }

    private String providerErrorSuffix(String responseBody) {
        String detail = safeProviderError(responseBody);
        if (detail.isBlank()) {
            return "";
        }
        return ": " + detail;
    }

    private String safeProviderError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode error = OBJECT_MAPPER.readTree(responseBody).path("error");
            if (error.isMissingNode() || error.isNull()) {
                return "";
            }
            List<String> parts = new ArrayList<>();
            appendErrorField(parts, "code", error.path("code").asText(""));
            appendErrorField(parts, "type", error.path("type").asText(""));
            appendErrorField(parts, "message", error.path("message").asText(""));
            return limitErrorDetail(String.join(", ", parts));
        } catch (Exception ignored) {
            return "";
        }
    }

    private void appendErrorField(List<String> parts, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        parts.add(name + "=" + redactErrorValue(value));
    }

    private String redactErrorValue(String value) {
        return value
                .replaceAll("(?i)(api[_-]?key|token|authorization|bearer)\\s*[:=]\\s*[^,\\s}]+", "$1=<redacted>")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String limitErrorDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }
        if (detail.length() <= ERROR_DETAIL_MAX_LENGTH) {
            return detail;
        }
        return detail.substring(0, ERROR_DETAIL_MAX_LENGTH);
    }

    private record IndexedEmbedding(int index, EmbeddingVector vector) {
    }
}
