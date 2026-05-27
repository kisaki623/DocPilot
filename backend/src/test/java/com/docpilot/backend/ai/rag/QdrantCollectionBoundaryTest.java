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

class QdrantCollectionBoundaryTest {

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
    void shouldBuildCollectionInfoPath() {
        assertThat(new QdrantCollectionInfoRequestBuilder().path("docpilot chunks"))
                .isEqualTo("/collections/docpilot+chunks");
    }

    @Test
    void shouldBuildCreateCollectionPayloadShape() throws Exception {
        String json = new QdrantCollectionCreateRequestBuilder().buildJson(1536, "cosine");
        Map<String, Object> request = objectMapper.readValue(json, new TypeReference<>() {
        });
        Map<String, Object> vectors = castMap(request.get("vectors"));

        assertThat(vectors).containsEntry("size", 1536);
        assertThat(vectors).containsEntry("distance", "Cosine");
    }

    @Test
    void shouldParseCollectionInfoResponseWithoutBodyLeakage() {
        QdrantCollectionPreflightResult result = new QdrantCollectionResponseParser()
                .parseInfo(200, "{\"status\":\"ok\",\"result\":{\"points_count\":42}}");

        assertThat(result.exists()).isTrue();
        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.toString()).doesNotContain("points_count");
    }

    @Test
    void fakeServerShouldSupportReadOnlyCollectionCheck() throws Exception {
        startServer(false);
        send("GET", "/collections/docpilot_chunks", "");

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).method()).isEqualTo("GET");
        assertThat(requests.get(0).path()).isEqualTo("/collections/docpilot_chunks");
    }

    @Test
    void fakeServerShouldOnlyCreateWhenExplicitlyAllowed() throws Exception {
        startServer(true);
        send("GET", "/collections/docpilot_chunks", "");
        send("PUT", "/collections/docpilot_chunks", new QdrantCollectionCreateRequestBuilder().buildJson(128, "Cosine"));

        assertThat(requests).extracting(CapturedRequest::method).containsExactly("GET", "PUT");
        Map<String, Object> createBody = objectMapper.readValue(requests.get(1).body(), new TypeReference<>() {
        });
        assertThat(castMap(createBody.get("vectors"))).containsEntry("size", 128);
    }

    @Test
    void capturedRequestsShouldNotContainSensitiveHeadersInSummary() throws Exception {
        startServer(false);
        send("GET", "/collections/docpilot_chunks", "");

        assertThat(requests.get(0).toString())
                .doesNotContain("Authorization")
                .doesNotContain("api-key")
                .doesNotContain("secret");
    }

    private void startServer(boolean allowPut) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body));
            if ("PUT".equals(exchange.getRequestMethod()) && !allowPut) {
                sendJson(exchange, 405, "{\"status\":\"method_not_allowed\"}");
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        });
        server.start();
    }

    private void send(String method, String path, String body) throws Exception {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path))
                .method(method, java.net.http.HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        java.net.http.HttpClient.newHttpClient()
                .send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private record CapturedRequest(String method, String path, String body) {
    }
}
