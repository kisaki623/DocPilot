package com.docpilot.backend.ai.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiCompatibleEmbeddingModelTest {

    @Test
    void shouldBuildEmbeddingRequest() {
        OpenAiCompatibleEmbeddingModel model = new OpenAiCompatibleEmbeddingModel(
                "embedding-model",
                "https://example.invalid/v1",
                "test-key-not-used",
                2000,
                5000
        );

        OpenAiCompatibleEmbeddingRequest request = model.buildRequest("hello");

        assertThat(request.model()).isEqualTo("embedding-model");
        assertThat(request.input()).isEqualTo("hello");
        assertThat(model.getBaseUrl()).isEqualTo("https://example.invalid/v1");
        assertThat(model.hasApiKey()).isTrue();
        assertThat(model.getConnectTimeoutMs()).isEqualTo(2000);
        assertThat(model.getRequestTimeoutMs()).isEqualTo(5000);
    }

    @Test
    void shouldFailWithoutNetworkWhenConfigurationMissing() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (StubEmbeddingProvider ignored = StubEmbeddingProvider.ok(validEmbeddingResponse(), requestCount)) {
            OpenAiCompatibleEmbeddingModel model = new OpenAiCompatibleEmbeddingModel(
                    "embedding-model",
                    "https://example.invalid/v1",
                    "",
                    2000,
                    5000
            );

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> model.embed("hello"));

            assertThat(ex.getMessage()).contains("not fully configured");
            assertThat(requestCount).hasValue(0);
        }
    }

    @Test
    void shouldPostEmbeddingRequestAndParseResponse() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        try (StubEmbeddingProvider provider = StubEmbeddingProvider.ok(
                validEmbeddingResponse(),
                requestCount,
                requestBody,
                authorization
        )) {
            OpenAiCompatibleEmbeddingModel model = configuredModel(provider.baseUrl());

            EmbeddingVector vector = model.embed("safe text");

            assertThat(vector.values()).containsExactly(0.1d, -0.2d, 0.3d);
            assertThat(requestCount).hasValue(1);
            assertThat(requestBody.get()).contains("\"model\":\"embedding-model\"");
            assertThat(requestBody.get()).contains("\"input\":\"safe text\"");
            assertThat(authorization.get()).startsWith("Bearer ");
        }
    }

    @Test
    void shouldParseEmbeddingResponse() {
        OpenAiCompatibleEmbeddingModel model = configuredModel("https://example.invalid/v1");

        EmbeddingVector vector = model.parseEmbedding(validEmbeddingResponse());

        assertThat(vector.dimension()).isEqualTo(3);
        assertThat(vector.values()).containsExactly(0.1d, -0.2d, 0.3d);
    }

    @Test
    void shouldFailSafelyForNon2xxStatus() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (StubEmbeddingProvider provider = StubEmbeddingProvider.of(500, "{\"error\":\"failed\"}", requestCount)) {
            OpenAiCompatibleEmbeddingModel model = configuredModel(provider.baseUrl());

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> model.embed("hello"));

            assertThat(ex.getMessage()).contains("status 500");
            assertThat(requestCount).hasValue(1);
        }
    }

    @Test
    void shouldFailSafelyForInvalidResponse() {
        OpenAiCompatibleEmbeddingModel model = configuredModel("https://example.invalid/v1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> model.parseEmbedding("not-json"));

        assertThat(ex.getMessage()).contains("Unable to parse");
    }

    private OpenAiCompatibleEmbeddingModel configuredModel(String baseUrl) {
        return new OpenAiCompatibleEmbeddingModel(
                "embedding-model",
                baseUrl,
                "test-key-not-used",
                2000,
                5000
        );
    }

    private static String validEmbeddingResponse() {
        return """
                {"data":[{"embedding":[0.1,-0.2,0.3],"index":0}],"model":"embedding-model"}
                """;
    }

    private record StubEmbeddingProvider(HttpServer server,
                                         AtomicInteger requestCount,
                                         AtomicReference<String> requestBody,
                                         AtomicReference<String> authorization) implements AutoCloseable {

        static StubEmbeddingProvider ok(String responseBody, AtomicInteger requestCount) throws IOException {
            return of(200, responseBody, requestCount);
        }

        static StubEmbeddingProvider ok(String responseBody,
                                        AtomicInteger requestCount,
                                        AtomicReference<String> requestBody,
                                        AtomicReference<String> authorization) throws IOException {
            return of(200, responseBody, requestCount, requestBody, authorization);
        }

        static StubEmbeddingProvider of(int statusCode, String responseBody, AtomicInteger requestCount) throws IOException {
            return of(statusCode, responseBody, requestCount, new AtomicReference<>(), new AtomicReference<>());
        }

        static StubEmbeddingProvider of(int statusCode,
                                        String responseBody,
                                        AtomicInteger requestCount,
                                        AtomicReference<String> requestBody,
                                        AtomicReference<String> authorization) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/embeddings", exchange -> {
                requestCount.incrementAndGet();
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return new StubEmbeddingProvider(server, requestCount, requestBody, authorization);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
