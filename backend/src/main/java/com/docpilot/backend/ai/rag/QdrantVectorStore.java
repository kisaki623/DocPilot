package com.docpilot.backend.ai.rag;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class QdrantVectorStore implements VectorStore {

    private static final String USER_ID = QdrantPointPayload.DEFAULT_USER_ID;
    private static final String DOCUMENT_VERSION = QdrantPointPayload.DEFAULT_DOCUMENT_VERSION;

    private final RagVectorStoreProperties.Qdrant properties;
    private final HttpClient httpClient;
    private final QdrantUpsertRequestBuilder upsertRequestBuilder;
    private final QdrantSearchRequestBuilder searchRequestBuilder;
    private final QdrantSearchResponseParser searchResponseParser;

    public QdrantVectorStore(RagVectorStoreProperties.Qdrant properties) {
        this(properties, null, new QdrantUpsertRequestBuilder(), new QdrantSearchRequestBuilder(),
                new QdrantSearchResponseParser());
    }

    QdrantVectorStore(RagVectorStoreProperties.Qdrant properties,
                      HttpClient httpClient,
                      QdrantUpsertRequestBuilder upsertRequestBuilder,
                      QdrantSearchRequestBuilder searchRequestBuilder,
                      QdrantSearchResponseParser searchResponseParser) {
        this.properties = properties == null ? new RagVectorStoreProperties.Qdrant() : properties;
        validateEndpoint(this.properties);
        this.httpClient = httpClient == null ? defaultHttpClient(this.properties) : httpClient;
        this.upsertRequestBuilder = upsertRequestBuilder == null ? new QdrantUpsertRequestBuilder() : upsertRequestBuilder;
        this.searchRequestBuilder = searchRequestBuilder == null ? new QdrantSearchRequestBuilder() : searchRequestBuilder;
        this.searchResponseParser = searchResponseParser == null ? new QdrantSearchResponseParser() : searchResponseParser;
    }

    @Override
    public void add(RagSearchScope scope, DocumentChunk chunk, EmbeddingVector vector) {
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        if (chunk == null) {
            throw new IllegalArgumentException("chunk must not be null");
        }
        if (!scope.documentId().equals(chunk.documentId())) {
            throw new IllegalArgumentException("scope documentId must match chunk documentId");
        }
        String body = upsertRequestBuilder.buildJson(List.of(
                QdrantPointPayload.fromChunk(scope.userId(), DOCUMENT_VERSION, chunk, vector)
        ));
        HttpRequest request = requestBuilder(upsertUri())
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        sendExpectSuccess(request, "upsert");
    }

    @Override
    public List<VectorSearchResult> searchTopK(RagSearchScope scope, EmbeddingVector queryVector, int topK) {
        if (topK <= 0) {
            return List.of();
        }
        String body = searchRequestBuilder.buildJson(scope, queryVector, topK);
        HttpRequest request = requestBuilder(searchUri())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        String responseBody = sendExpectSuccess(request, "search");
        return searchResponseParser.parsePoints(responseBody).stream()
                .limit(topK)
                .map(QdrantRetrievedPoint::toVectorSearchResult)
                .toList();
    }

    @Override
    public void deleteDocument(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        String body = "{\"filter\":{\"must\":["
                + "{\"key\":\"userId\",\"match\":{\"value\":\"" + USER_ID + "\"}},"
                + "{\"key\":\"documentId\",\"match\":{\"value\":" + documentId + "}}"
                + "]}}";
        HttpRequest request = requestBuilder(deleteUri())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        sendExpectSuccess(request, "delete");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Qdrant vector store clear is not supported by the adapter.");
    }

    private HttpClient defaultHttpClient(RagVectorStoreProperties.Qdrant properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .header("Content-Type", "application/json");
        if (!properties.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getApiKey());
        }
        return builder;
    }

    private String sendExpectSuccess(HttpRequest request, String operation) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Qdrant " + operation + " request failed with status " + response.statusCode() + ".");
            }
            return response.body();
        } catch (IOException ex) {
            throw new IllegalStateException("Qdrant " + operation + " request failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant " + operation + " request was interrupted.", ex);
        }
    }

    private URI upsertUri() {
        return endpointUri("/collections/" + collection() + "/points?wait=true");
    }

    private URI searchUri() {
        return endpointUri("/collections/" + collection() + "/points/search");
    }

    private URI deleteUri() {
        return endpointUri("/collections/" + collection() + "/points/delete?wait=true");
    }

    private URI endpointUri(String pathAndQuery) {
        return URI.create(stripTrailingSlash(properties.getEndpoint()) + pathAndQuery);
    }

    private String collection() {
        return URLEncoder.encode(properties.getCollection(), StandardCharsets.UTF_8);
    }

    private String stripTrailingSlash(String endpoint) {
        String normalized = endpoint.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void validateEndpoint(RagVectorStoreProperties.Qdrant properties) {
        if (properties.getEndpoint().isBlank()) {
            throw new IllegalStateException("Qdrant vector store endpoint is required when provider=qdrant.");
        }
        URI uri = URI.create(properties.getEndpoint());
        if (uri.getScheme() == null || uri.getHost() == null
                || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("Qdrant vector store endpoint must be an absolute HTTP URL.");
        }
    }
}
