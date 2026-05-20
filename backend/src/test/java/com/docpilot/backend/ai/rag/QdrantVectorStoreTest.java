package com.docpilot.backend.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QdrantVectorStoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private final List<CapturedRequest> requests = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendUpsertRequestToLocalFakeServer() throws Exception {
        startServer(exchange -> sendJson(exchange, 200, "{\"status\":\"ok\"}"));
        QdrantVectorStore vectorStore = new QdrantVectorStore(properties(false));
        DocumentChunk chunk = new DocumentChunk(61L, 0, "sanitized chunk text",
                Map.of("contentHash", "hash-0", "charStart", "0", "charEnd", "22"));

        vectorStore.add(chunk, vector(0.1D, 0.2D));

        CapturedRequest request = requests.get(0);
        Map<String, Object> body = readMap(request.body());
        Map<String, Object> point = firstPoint(body);
        Map<String, Object> payload = castMap(point.get("payload"));

        assertThat(request.method()).isEqualTo("PUT");
        assertThat(request.path()).isEqualTo("/collections/docpilot_test/points");
        assertThat(request.query()).isEqualTo("wait=true");
        assertThat(point.get("vector")).isEqualTo(List.of(0.1D, 0.2D));
        assertThat(payload)
                .containsEntry("documentId", 61)
                .containsEntry("chunkIndex", 0)
                .containsEntry("contentHash", "hash-0");
    }

    @Test
    void shouldSendSearchRequestAndParseTopKFromLocalFakeServer() throws Exception {
        startServer(exchange -> sendJson(exchange, 200, """
                {
                  "result": [
                    {
                      "id": "61:default:0:hash-0",
                      "score": 0.99,
                      "payload": {
                        "documentId": 61,
                        "chunkIndex": 0,
                        "text": "sanitized chunk text",
                        "metadata": {
                          "contentHash": "hash-0",
                          "charStart": "0",
                          "charEnd": "22"
                        }
                      }
                    }
                  ]
                }
                """));
        QdrantVectorStore vectorStore = new QdrantVectorStore(properties(false));

        List<VectorSearchResult> results = vectorStore.searchTopK(61L, vector(0.1D, 0.2D), 1);

        CapturedRequest request = requests.get(0);
        Map<String, Object> body = readMap(request.body());
        Map<String, Object> filter = castMap(body.get("filter"));
        List<Map<String, Object>> must = castList(filter.get("must"));

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/collections/docpilot_test/points/search");
        assertThat(body.get("limit")).isEqualTo(1);
        assertThat(must).anySatisfy(condition -> assertThat(condition.get("key")).isEqualTo("userId"));
        assertThat(must).anySatisfy(condition -> assertThat(condition.get("key")).isEqualTo("documentId"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isEqualTo(0.99D);
        assertThat(results.get(0).chunk().documentId()).isEqualTo(61L);
    }

    @Test
    void shouldIndexAndSearchAgainstSameLocalFakeServer() throws Exception {
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/points/search")) {
                sendJson(exchange, 200, """
                        {
                          "result": {
                            "points": [
                              {
                                "id": "61:default:1:hash-1",
                                "score": 0.88,
                                "payload": {
                                  "documentId": 61,
                                  "chunkIndex": 1,
                                  "text": "sanitized chunk text",
                                  "metadata": {
                                    "contentHash": "hash-1",
                                    "charStart": "11",
                                    "charEnd": "33",
                                    "source": "unit-test"
                                  }
                                }
                              }
                            ]
                          }
                        }
                        """);
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        });
        QdrantVectorStore vectorStore = new QdrantVectorStore(properties(false));
        DocumentChunk chunk = new DocumentChunk(61L, 1, "sanitized chunk text",
                Map.of("contentHash", "hash-1", "charStart", "11", "charEnd", "33", "source", "unit-test"));

        vectorStore.add(chunk, vector(0.3D, 0.4D));
        List<VectorSearchResult> results = vectorStore.searchTopK(61L, vector(0.3D, 0.4D), 2);

        assertThat(requests).hasSize(2);
        CapturedRequest upsertRequest = requests.get(0);
        CapturedRequest searchRequest = requests.get(1);
        Map<String, Object> upsertBody = readMap(upsertRequest.body());
        Map<String, Object> searchBody = readMap(searchRequest.body());
        Map<String, Object> point = firstPoint(upsertBody);
        Map<String, Object> payload = castMap(point.get("payload"));
        Map<String, Object> citation = castMap(payload.get("citation"));
        List<Map<String, Object>> must = castList(castMap(searchBody.get("filter")).get("must"));

        assertThat(upsertRequest.method()).isEqualTo("PUT");
        assertThat(upsertRequest.path()).isEqualTo("/collections/docpilot_test/points");
        assertThat(point.get("id")).isEqualTo("61:default:1:hash-1");
        assertThat(point.get("vector")).isEqualTo(List.of(0.3D, 0.4D));
        assertThat(payload)
                .containsEntry("userId", "system")
                .containsEntry("documentId", 61)
                .containsEntry("documentVersion", "default")
                .containsEntry("chunkIndex", 1)
                .containsEntry("contentHash", "hash-1");
        assertThat(citation)
                .containsEntry("charStart", "11")
                .containsEntry("charEnd", "33")
                .containsEntry("source", "unit-test");

        assertThat(searchRequest.method()).isEqualTo("POST");
        assertThat(searchRequest.path()).isEqualTo("/collections/docpilot_test/points/search");
        assertThat(searchBody.get("vector")).isEqualTo(List.of(0.3D, 0.4D));
        assertThat(searchBody.get("limit")).isEqualTo(2);
        assertThat(must).anySatisfy(condition -> {
            assertThat(condition.get("key")).isEqualTo("userId");
            assertThat(castMap(condition.get("match")).get("value")).isEqualTo("system");
        });
        assertThat(must).anySatisfy(condition -> {
            assertThat(condition.get("key")).isEqualTo("documentId");
            assertThat(castMap(condition.get("match")).get("value")).isEqualTo(61);
        });
        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isEqualTo(0.88D);
        assertThat(results.get(0).chunk().metadata())
                .containsEntry("contentHash", "hash-1")
                .containsEntry("source", "unit-test");
    }

    @Test
    void shouldUseAuthorizationHeaderWithoutLeakingItInFailureMessage() throws Exception {
        startServer(exchange -> sendJson(exchange, 500, "{\"error\":\"do-not-print\"}"));
        QdrantVectorStore vectorStore = new QdrantVectorStore(properties(true));

        assertThatThrownBy(() -> vectorStore.searchTopK(61L, vector(0.1D, 0.2D), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Qdrant search request failed with status 500.")
                .hasMessageNotContaining("secret-test-token")
                .hasMessageNotContaining("do-not-print");
        assertThat(requests.get(0).authorizationPresent()).isTrue();
    }

    private RagVectorStoreProperties.Qdrant properties(boolean withApiKey) {
        RagVectorStoreProperties.Qdrant properties = new RagVectorStoreProperties.Qdrant();
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setCollection("docpilot_test");
        properties.setRequestTimeoutMs(3000);
        properties.setConnectTimeoutMs(1000);
        if (withApiKey) {
            properties.setApiKey("secret-test-token");
        }
        return properties;
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

    private EmbeddingVector vector(Double... values) {
        return new EmbeddingVector(List.of(values));
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
