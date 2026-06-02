package com.docpilot.backend.ai.rag.vector.qdrant;

import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.vector.VectorPoint;
import com.docpilot.backend.ai.rag.vector.VectorSearchRequest;
import com.docpilot.backend.ai.rag.vector.VectorSearchResult;
import com.docpilot.backend.ai.rag.vector.VectorStoreClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class QdrantVectorStoreClient implements VectorStoreClient {

    private final RagVectorStoreProperties.Qdrant properties;
    private final HttpClient httpClient;
    private final QdrantUpsertPointsRequestBuilder upsertRequestBuilder;
    private final QdrantSearchPointsRequestBuilder searchRequestBuilder;
    private final QdrantDeletePointsRequestBuilder deleteRequestBuilder;
    private final QdrantVectorSearchResponseParser responseParser;

    public QdrantVectorStoreClient(RagVectorStoreProperties.Qdrant properties) {
        this(properties, null, new QdrantUpsertPointsRequestBuilder(), new QdrantSearchPointsRequestBuilder(),
                new QdrantDeletePointsRequestBuilder(), new QdrantVectorSearchResponseParser());
    }

    QdrantVectorStoreClient(RagVectorStoreProperties.Qdrant properties,
                            HttpClient httpClient,
                            QdrantUpsertPointsRequestBuilder upsertRequestBuilder,
                            QdrantSearchPointsRequestBuilder searchRequestBuilder,
                            QdrantDeletePointsRequestBuilder deleteRequestBuilder,
                            QdrantVectorSearchResponseParser responseParser) {
        this.properties = properties == null ? new RagVectorStoreProperties.Qdrant() : properties;
        validateEndpoint();
        this.httpClient = httpClient == null ? defaultHttpClient(this.properties) : httpClient;
        this.upsertRequestBuilder = upsertRequestBuilder == null ? new QdrantUpsertPointsRequestBuilder() : upsertRequestBuilder;
        this.searchRequestBuilder = searchRequestBuilder == null ? new QdrantSearchPointsRequestBuilder() : searchRequestBuilder;
        this.deleteRequestBuilder = deleteRequestBuilder == null ? new QdrantDeletePointsRequestBuilder() : deleteRequestBuilder;
        this.responseParser = responseParser == null ? new QdrantVectorSearchResponseParser() : responseParser;
    }

    @Override
    public void upsert(List<VectorPoint> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
        points.forEach(this::validateDimension);
        String body = upsertRequestBuilder.buildJson(points);
        HttpRequest request = requestBuilder(upsertUri())
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        sendExpectSuccess(request, "upsert");
    }

    @Override
    public VectorSearchResult search(VectorSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        validateDimension(request);
        String body = searchRequestBuilder.buildJson(request);
        HttpRequest httpRequest = requestBuilder(searchUri())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        String responseBody = sendExpectSuccess(httpRequest, "search");
        return new VectorSearchResult(
                responseParser.parseHits(responseBody).stream().limit(request.topK()).toList(),
                "qdrant",
                properties.getCollection()
        );
    }

    @Override
    public void deleteByDocumentId(Long userId, Long documentId, Integer indexVersion) {
        String body = deleteRequestBuilder.buildJson(userId, documentId, indexVersion);
        HttpRequest request = requestBuilder(deleteUri())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        sendExpectSuccess(request, "delete");
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
                throw new IllegalStateException("Qdrant vector store " + operation
                        + " request failed with status " + response.statusCode() + ".");
            }
            return response.body();
        } catch (IOException ex) {
            throw new IllegalStateException("Qdrant vector store " + operation + " request failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant vector store " + operation + " request was interrupted.", ex);
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
        return URI.create(stripTrailingSlash(endpoint()) + pathAndQuery);
    }

    private String endpoint() {
        if (!properties.getEndpoint().isBlank()) {
            return properties.getEndpoint();
        }
        return "http://" + properties.getHost() + ":" + properties.getPort();
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

    private void validateEndpoint() {
        if (properties.getEndpoint().isBlank() && properties.getHost().isBlank()) {
            throw new IllegalStateException("Qdrant vector store endpoint or host is required when provider=qdrant.");
        }
        URI uri = URI.create(endpoint());
        if (uri.getScheme() == null || uri.getHost() == null
                || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("Qdrant vector store endpoint must be an absolute HTTP URL.");
        }
    }

    private void validateDimension(VectorPoint point) {
        if (point.vector().dimension() != properties.getDimension()) {
            throw new IllegalArgumentException("point vector dimension must match app.rag.vector-store.qdrant.dimension");
        }
    }

    private void validateDimension(VectorSearchRequest request) {
        if (request.queryVector().dimension() != properties.getDimension()) {
            throw new IllegalArgumentException("query vector dimension must match app.rag.vector-store.qdrant.dimension");
        }
    }
}
