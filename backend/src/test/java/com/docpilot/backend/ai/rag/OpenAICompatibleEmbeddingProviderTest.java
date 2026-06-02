package com.docpilot.backend.ai.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAICompatibleEmbeddingProviderTest {

    @Test
    void shouldFailWithoutNetworkWhenConfigurationMissing() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (StubEmbeddingServer ignored = StubEmbeddingServer.ok(validSingleResponse(), requestCount)) {
            OpenAICompatibleEmbeddingProvider provider = new OpenAICompatibleEmbeddingProvider(openAiProperties(""));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> provider.embed(EmbeddingRequest.of("hello")));

            assertThat(ex.getMessage()).contains("not fully configured");
            assertThat(requestCount).hasValue(0);
        }
    }

    @Test
    void shouldPostSingleEmbeddingRequestAndParseResponse() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        try (StubEmbeddingServer server = StubEmbeddingServer.ok(validSingleResponse(), requestCount, requestBody, authorization)) {
            OpenAICompatibleEmbeddingProvider provider = new OpenAICompatibleEmbeddingProvider(openAiProperties(server.baseUrl()));

            EmbeddingResult result = provider.embed(new EmbeddingRequest("safe text", "", Map.of("chunkIndex", "0")));

            assertThat(result.vector().values()).containsExactly(0.1d, -0.2d, 0.3d);
            assertThat(result.provider()).isEqualTo(OpenAICompatibleEmbeddingProvider.PROVIDER);
            assertThat(result.model()).isEqualTo("embedding-model");
            assertThat(result.metadata()).containsEntry("chunkIndex", "0");
            assertThat(requestCount).hasValue(1);
            assertThat(requestBody.get()).contains("\"model\":\"embedding-model\"");
            assertThat(requestBody.get()).contains("\"input\":\"safe text\"");
            assertThat(authorization.get()).startsWith("Bearer ");
        }
    }

    @Test
    void shouldPostBatchEmbeddingRequestAndKeepOrderByIndex() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        try (StubEmbeddingServer server = StubEmbeddingServer.ok(validBatchResponseOutOfOrder(), requestCount, requestBody, new AtomicReference<>())) {
            OpenAICompatibleEmbeddingProvider provider = new OpenAICompatibleEmbeddingProvider(openAiProperties(server.baseUrl()));

            List<EmbeddingResult> results = provider.embedBatch(List.of(
                    new EmbeddingRequest("first", "", Map.of("chunkIndex", "0")),
                    new EmbeddingRequest("second", "", Map.of("chunkIndex", "1"))
            ));

            assertThat(results).hasSize(2);
            assertThat(results.get(0).vector().values()).containsExactly(0.1d, 0.2d);
            assertThat(results.get(1).vector().values()).containsExactly(0.3d, 0.4d);
            assertThat(results).extracting(result -> result.metadata().get("chunkIndex"))
                    .containsExactly("0", "1");
            assertThat(requestCount).hasValue(1);
            assertThat(requestBody.get()).contains("\"input\":[\"first\",\"second\"]");
        }
    }

    @Test
    void shouldParseEmbeddingResponse() {
        OpenAICompatibleEmbeddingProvider provider = new OpenAICompatibleEmbeddingProvider(openAiProperties("https://example.invalid/v1"));

        EmbeddingVector vector = provider.parseEmbedding(validSingleResponse());

        assertThat(vector.values()).containsExactly(0.1d, -0.2d, 0.3d);
    }

    @Test
    void shouldFailSafelyForNon2xxStatus() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (StubEmbeddingServer server = StubEmbeddingServer.of(500, "{\"error\":\"failed\"}", requestCount)) {
            OpenAICompatibleEmbeddingProvider provider = new OpenAICompatibleEmbeddingProvider(openAiProperties(server.baseUrl()));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> provider.embed(EmbeddingRequest.of("hello")));

            assertThat(ex.getMessage()).contains("status 500");
            assertThat(requestCount).hasValue(1);
        }
    }

    @Test
    void shouldFailSafelyForInvalidResponse() {
        OpenAICompatibleEmbeddingProvider provider = new OpenAICompatibleEmbeddingProvider(openAiProperties("https://example.invalid/v1"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> provider.parseEmbedding("not-json"));

        assertThat(ex.getMessage()).contains("Unable to parse");
    }

    private EmbeddingProperties openAiProperties(String baseUrl) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setProvider("openai_compatible");
        properties.setModel("embedding-model");
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("test-key-not-used");
        properties.setConnectTimeoutMs(2000);
        properties.setRequestTimeoutMs(5000);
        return properties;
    }

    private static String validSingleResponse() {
        return """
                {"data":[{"embedding":[0.1,-0.2,0.3],"index":0}],"model":"embedding-model"}
                """;
    }

    private static String validBatchResponseOutOfOrder() {
        return """
                {"data":[{"embedding":[0.3,0.4],"index":1},{"embedding":[0.1,0.2],"index":0}],"model":"embedding-model"}
                """;
    }

    private record StubEmbeddingServer(HttpServer server,
                                       AtomicInteger requestCount,
                                       AtomicReference<String> requestBody,
                                       AtomicReference<String> authorization) implements AutoCloseable {

        static StubEmbeddingServer ok(String responseBody, AtomicInteger requestCount) throws IOException {
            return of(200, responseBody, requestCount);
        }

        static StubEmbeddingServer ok(String responseBody,
                                      AtomicInteger requestCount,
                                      AtomicReference<String> requestBody,
                                      AtomicReference<String> authorization) throws IOException {
            return of(200, responseBody, requestCount, requestBody, authorization);
        }

        static StubEmbeddingServer of(int statusCode, String responseBody, AtomicInteger requestCount) throws IOException {
            return of(statusCode, responseBody, requestCount, new AtomicReference<>(), new AtomicReference<>());
        }

        static StubEmbeddingServer of(int statusCode,
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
            return new StubEmbeddingServer(server, requestCount, requestBody, authorization);
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
