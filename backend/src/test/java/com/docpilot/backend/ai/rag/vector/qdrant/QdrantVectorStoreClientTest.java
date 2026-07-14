package com.docpilot.backend.ai.rag.vector.qdrant;

import com.docpilot.backend.ai.rag.EmbeddingVector;
import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.vector.VectorPoint;
import com.docpilot.backend.ai.rag.vector.VectorSearchRequest;
import com.docpilot.backend.ai.rag.vector.VectorSearchResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QdrantVectorStoreClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<CapturedRequest> requests = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldCreateCollectionWhenMissing() throws Exception {
        startServer(exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 404, "{\"status\":\"not_found\"}");
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        });
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties(false));

        client.ensureCollection();

        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).method()).isEqualTo("GET");
        assertThat(requests.get(0).path()).isEqualTo("/collections/docpilot_test");
        assertThat(requests.get(1).method()).isEqualTo("PUT");
        assertThat(requests.get(1).path()).isEqualTo("/collections/docpilot_test");

        Map<String, Object> body = readMap(requests.get(1).body());
        Map<String, Object> vectors = castMap(body.get("vectors"));
        assertThat(vectors)
                .containsEntry("size", 2)
                .containsEntry("distance", "Cosine");
    }

    @Test
    void shouldSkipCollectionCreateWhenAlreadyExists() throws Exception {
        startServer(exchange -> sendJson(exchange, 200, "{\"result\":{}}"));
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties(false));

        client.ensureCollection();

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).method()).isEqualTo("GET");
        assertThat(requests.get(0).path()).isEqualTo("/collections/docpilot_test");
    }

    @Test
    void ensureReadyShouldCheckCollection() throws Exception {
        startServer(exchange -> sendJson(exchange, 200, "{\"result\":{}}"));
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties(false));

        client.ensureReady();

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).method()).isEqualTo("GET");
        assertThat(requests.get(0).path()).isEqualTo("/collections/docpilot_test");
    }

    @Test
    void shouldDeleteCollectionIfExistsAndIgnoreMissingCollection() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        startServer(exchange -> sendJson(exchange, calls.getAndIncrement() == 0 ? 200 : 404, "{\"status\":\"ok\"}"));
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties(false));

        client.deleteCollectionIfExists();
        client.deleteCollectionIfExists();

        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.method()).isEqualTo("DELETE");
            assertThat(request.path()).isEqualTo("/collections/docpilot_test");
        });
    }

    @Test
    void shouldSendUpsertRequestToLocalStub() throws Exception {
        startServer(exchange -> sendJson(exchange, 200, "{\"status\":\"ok\"}"));
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties(false));

        client.upsert(List.of(point("550e8400-e29b-41d4-a716-446655440000", 1L, 61L, 1, 0, "chunk text")));

        CapturedRequest request = requests.get(0);
        Map<String, Object> body = readMap(request.body());
        Map<String, Object> firstPoint = firstPoint(body);
        Map<String, Object> payload = castMap(firstPoint.get("payload"));

        assertThat(request.method()).isEqualTo("PUT");
        assertThat(request.path()).isEqualTo("/collections/docpilot_test/points");
        assertThat(request.query()).isEqualTo("wait=true");
        assertThat(firstPoint.get("id")).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(firstPoint.get("vector")).isEqualTo(List.of(0.1D, 0.2D));
        assertThat(payload)
                .containsEntry("userId", 1)
                .containsEntry("documentId", 61)
                .containsEntry("indexVersion", 1)
                .containsEntry("chunkIndex", 0)
                .containsEntry("content", "chunk text")
                .containsEntry("contentHash", "hash-0");
    }

    @Test
    void shouldSendSearchFilterAndParseHits() throws Exception {
        startServer(exchange -> sendJson(exchange, 200, """
                {
                  "result": [
                    {
                      "id": "550e8400-e29b-41d4-a716-446655440000",
                      "score": 0.98,
                      "payload": {
                        "userId": 1,
                        "documentId": 61,
                        "indexVersion": 1,
                        "chunkIndex": 0,
                        "content": "chunk text",
                        "contentHash": "hash-0"
                      }
                    }
                  ]
                }
                """));
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties(false));

        VectorSearchResult result = client.search(new VectorSearchRequest(1L, 61L, 1, vector(0.1D, 0.2D), 3));

        CapturedRequest request = requests.get(0);
        Map<String, Object> body = readMap(request.body());
        List<Map<String, Object>> must = castList(castMap(body.get("filter")).get("must"));

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/collections/docpilot_test/points/search");
        assertThat(body.get("limit")).isEqualTo(3);
        assertThat(must).anySatisfy(condition -> assertMatch(condition, "userId", 1));
        assertThat(must).anySatisfy(condition -> assertMatch(condition, "documentId", 61));
        assertThat(must).anySatisfy(condition -> assertMatch(condition, "indexVersion", 1));
        assertThat(result.provider()).isEqualTo("qdrant");
        assertThat(result.collection()).isEqualTo("docpilot_test");
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).score()).isEqualTo(0.98D);
        assertThat(result.hits().get(0).content()).isEqualTo("chunk text");
    }

    @Test
    void shouldOmitVersionFilterWhenVersionIsNull() throws Exception {
        startServer(exchange -> sendJson(exchange, 200, "{\"result\":[]}"));
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties(false));

        client.search(new VectorSearchRequest(1L, 61L, null, vector(0.1D, 0.2D), 3));

        List<Map<String, Object>> must = castList(castMap(readMap(requests.get(0).body()).get("filter")).get("must"));
        assertThat(must).anySatisfy(condition -> assertMatch(condition, "userId", 1));
        assertThat(must).anySatisfy(condition -> assertMatch(condition, "documentId", 61));
        assertThat(must).noneSatisfy(condition -> assertThat(condition.get("key")).isEqualTo("indexVersion"));
    }

    @Test
    void shouldSendSearchFilterWithDocumentIdsMatchAny() throws Exception {
        startServer(exchange -> sendJson(exchange, 200, "{\"result\":[]}"));
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties(false));

        client.search(VectorSearchRequest.forDocuments(1L, List.of(61L, 62L), 1, vector(0.1D, 0.2D), 3));

        List<Map<String, Object>> must = castList(castMap(readMap(requests.get(0).body()).get("filter")).get("must"));
        assertThat(must).anySatisfy(condition -> {
            assertThat(condition.get("key")).isEqualTo("documentId");
            assertThat(castMap(condition.get("match")).get("any")).isEqualTo(List.of(61, 62));
        });
        assertThat(must).anySatisfy(condition -> assertMatch(condition, "userId", 1));
        assertThat(must).anySatisfy(condition -> assertMatch(condition, "indexVersion", 1));
    }

    @Test
    void shouldSendDeleteByDocumentFilter() throws Exception {
        startServer(exchange -> sendJson(exchange, 200, "{\"status\":\"ok\"}"));
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties(false));

        client.deleteByDocumentId(1L, 61L, 1);

        CapturedRequest request = requests.get(0);
        List<Map<String, Object>> must = castList(castMap(readMap(request.body()).get("filter")).get("must"));
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/collections/docpilot_test/points/delete");
        assertThat(request.query()).isEqualTo("wait=true");
        assertThat(must).anySatisfy(condition -> assertMatch(condition, "userId", 1));
        assertThat(must).anySatisfy(condition -> assertMatch(condition, "documentId", 61));
        assertThat(must).anySatisfy(condition -> assertMatch(condition, "indexVersion", 1));
    }

    @Test
    void shouldUseAuthorizationHeaderWithoutLeakingSecretInFailureMessage() throws Exception {
        startServer(exchange -> sendJson(exchange, 500, "{\"error\":\"do-not-print\"}"));
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties(true));

        assertThatThrownBy(() -> client.search(new VectorSearchRequest(1L, 61L, 1, vector(0.1D, 0.2D), 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Qdrant vector store search request failed with status 500.")
                .hasMessageNotContaining("secret-test-token")
                .hasMessageNotContaining("do-not-print")
                .hasMessageNotContaining("127.0.0.1");
        assertThat(requests.get(0).authorizationPresent()).isTrue();
    }

    @Test
    void shouldFailFastWithoutEndpointOrHost() {
        RagVectorStoreProperties.Qdrant properties = new RagVectorStoreProperties.Qdrant();
        properties.setEndpoint("");
        properties.setHost("");

        assertThatThrownBy(() -> new QdrantVectorStoreClient(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Qdrant vector store endpoint or host is required when provider=qdrant.");
        assertThat(requests).isEmpty();
    }

    @Test
    void shouldRejectDimensionMismatchBeforeHttpRequest() throws Exception {
        startServer(exchange -> sendJson(exchange, 200, "{\"status\":\"ok\"}"));
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties(false));

        assertThatThrownBy(() -> client.upsert(List.of(new VectorPoint("p", 1L, 61L, 1, 0,
                "chunk", "hash", vector(0.1D, 0.2D, 0.3D), Map.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("point vector dimension must match app.rag.vector-store.qdrant.dimension");
        assertThat(requests).isEmpty();
    }

    private RagVectorStoreProperties.Qdrant properties(boolean withApiKey) {
        RagVectorStoreProperties.Qdrant properties = new RagVectorStoreProperties.Qdrant();
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setCollection("docpilot_test");
        properties.setDimension(2);
        properties.setRequestTimeoutMs(3000);
        properties.setConnectTimeoutMs(1000);
        if (withApiKey) {
            properties.setApiKey("secret-test-token");
        }
        return properties;
    }

    private VectorPoint point(String id,
                              Long userId,
                              Long documentId,
                              Integer indexVersion,
                              Integer chunkIndex,
                              String content) {
        return new VectorPoint(id, userId, documentId, indexVersion, chunkIndex, content,
                "hash-" + chunkIndex, vector(0.1D, 0.2D), Map.of("tokenCount", 12));
    }

    private EmbeddingVector vector(Double... values) {
        return new EmbeddingVector(List.of(values));
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(),
                    body,
                    exchange.getRequestHeaders().containsKey("Authorization")
            ));
            handler.handle(exchange);
        });
        server.start();
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private Map<String, Object> readMap(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstPoint(Map<String, Object> request) {
        return ((List<Map<String, Object>>) request.get("points")).get(0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private void assertMatch(Map<String, Object> condition, String key, Object value) {
        assertThat(condition.get("key")).isEqualTo(key);
        assertThat(castMap(condition.get("match")).get("value")).isEqualTo(value);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private record CapturedRequest(
            String method,
            String path,
            String query,
            String body,
            boolean authorizationPresent
    ) {
    }
}
