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

class QdrantRagQaContextIntegrationTest {

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
    void shouldBuildRagQaContextThroughQdrantVectorStoreAdapter() throws Exception {
        startServer();
        RagQaContextBuilder builder = new RagQaContextBuilder(
                new EmbeddingModelFactory(),
                new RagEmbeddingProperties(),
                new InMemoryVectorStore(),
                new RagIndexManager(),
                qdrantProperties(),
                new VectorStoreFactory()
        );

        RagQaContext context = builder.build(
                61L,
                "Where is the cache evidence?",
                "Cache evidence is kept in session counters. Retrieval should cite sanitized metadata.",
                2,
                500
        );

        assertThat(requests).extracting(CapturedRequest::method)
                .contains("POST", "PUT");
        assertThat(requests).anySatisfy(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/collections/docpilot_context/points/delete");
        });
        assertThat(requests).anySatisfy(request -> {
            assertThat(request.method()).isEqualTo("PUT");
            assertThat(request.path()).isEqualTo("/collections/docpilot_context/points");
        });
        CapturedRequest searchRequest = requests.stream()
                .filter(request -> request.path().endsWith("/points/search"))
                .findFirst()
                .orElseThrow();
        Map<String, Object> searchBody = readMap(searchRequest.body());
        List<Map<String, Object>> must = castList(castMap(searchBody.get("filter")).get("must"));

        assertThat(searchBody.get("limit")).isEqualTo(2);
        assertThat(must).anySatisfy(condition -> {
            assertThat(condition.get("key")).isEqualTo("userId");
            assertThat(castMap(condition.get("match")).get("value")).isEqualTo("system");
        });
        assertThat(must).anySatisfy(condition -> {
            assertThat(condition.get("key")).isEqualTo("documentId");
            assertThat(castMap(condition.get("match")).get("value")).isEqualTo(61);
        });
        assertThat(context.used()).isTrue();
        assertThat(context.retrievedCount()).isEqualTo(1);
        assertThat(context.trace().contextHashPresent()).isTrue();
        assertThat(context.trace().vectorStoreType()).isEqualTo("qdrant");
        assertThat(context.citations()).hasSize(1);
        assertThat(context.citations().get(0).metadata())
                .containsEntry("contentHash", "hash-qdrant-1")
                .containsEntry("source", "fake-qdrant");
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            capture(exchange);
            if (exchange.getRequestURI().getPath().endsWith("/points/search")) {
                sendJson(exchange, 200, """
                        {
                          "result": [
                            {
                              "id": "61:default:0:hash-qdrant-1",
                              "score": 0.91,
                              "payload": {
                                "documentId": 61,
                                "chunkIndex": 0,
                                "text": "sanitized retrieval text",
                                "metadata": {
                                  "contentHash": "hash-qdrant-1",
                                  "charStart": "0",
                                  "charEnd": "24",
                                  "source": "fake-qdrant"
                                }
                              }
                            }
                          ]
                        }
                        """);
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        });
        server.start();
    }

    private void capture(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getQuery(),
                body
        ));
    }

    private RagVectorStoreProperties qdrantProperties() {
        RagVectorStoreProperties properties = new RagVectorStoreProperties();
        properties.setProvider("qdrant");
        RagVectorStoreProperties.Qdrant qdrant = new RagVectorStoreProperties.Qdrant();
        qdrant.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        qdrant.setCollection("docpilot_context");
        qdrant.setConnectTimeoutMs(1000);
        qdrant.setRequestTimeoutMs(3000);
        properties.setQdrant(qdrant);
        return properties;
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
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private record CapturedRequest(String method, String path, String query, String body) {
    }
}
