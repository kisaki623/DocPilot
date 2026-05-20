package com.docpilot.backend.ai.agent.tool;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleLlmToolSelectionClientTest {

    @Test
    void shouldBuildRequestWithPrompt() {
        OpenAiCompatibleLlmToolSelectionClient client = new OpenAiCompatibleLlmToolSelectionClient(
                "selector-model",
                "https://example.invalid/v1",
                "test-key-not-used",
                2000,
                5000,
                128,
                0.1d
        );

        OpenAiCompatibleToolSelectionRequest request = client.buildRequest("select a tool");

        assertThat(request.model()).isEqualTo("selector-model");
        assertThat(request.temperature()).isEqualTo(0.1d);
        assertThat(request.maxTokens()).isEqualTo(128);
        assertThat(request.stream()).isFalse();
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).role()).isEqualTo("system");
        assertThat(request.messages().get(1).role()).isEqualTo("user");
        assertThat(request.messages().get(1).content()).isEqualTo("select a tool");
        assertThat(client.getBaseUrl()).isEqualTo("https://example.invalid/v1");
        assertThat(client.hasApiKey()).isTrue();
        assertThat(client.getConnectTimeoutMs()).isEqualTo(2000);
        assertThat(client.getRequestTimeoutMs()).isEqualTo(5000);
        assertThat(client.getMaxTokens()).isEqualTo(128);
        assertThat(client.getTemperature()).isEqualTo(0.1d);
    }

    @Test
    void shouldReturnDisabledWithoutNetworkWhenApiKeyBlank() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (StubProvider provider = StubProvider.ok(validProviderResponse(), requestCount)) {
            OpenAiCompatibleLlmToolSelectionClient client = new OpenAiCompatibleLlmToolSelectionClient(
                    "selector-model",
                    provider.baseUrl(),
                    "",
                    2000,
                    5000,
                    128,
                    0.0d
            );

            LlmToolSelectionClientResponse response = client.completeSelectionPrompt("select a tool");

            assertThat(response.provider()).isEqualTo("openai_compatible");
            assertThat(response.model()).isEqualTo("selector-model");
            assertThat(response.disabled()).isTrue();
            assertThat(response.rawText()).isEmpty();
            assertThat(response.errorMessage()).contains("not fully configured");
            assertThat(requestCount).hasValue(0);
        }
    }

    @Test
    void shouldReturnDisabledWithoutNetworkWhenBaseUrlBlank() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (StubProvider ignored = StubProvider.ok(validProviderResponse(), requestCount)) {
            OpenAiCompatibleLlmToolSelectionClient client = new OpenAiCompatibleLlmToolSelectionClient(
                    "selector-model",
                    "",
                    "test-key-not-used",
                    2000,
                    5000,
                    128,
                    0.0d
            );

            LlmToolSelectionClientResponse response = client.completeSelectionPrompt("select a tool");

            assertThat(response.disabled()).isTrue();
            assertThat(requestCount).hasValue(0);
        }
    }

    @Test
    void shouldReturnDisabledWithoutNetworkWhenModelBlank() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (StubProvider ignored = StubProvider.ok(validProviderResponse(), requestCount)) {
            OpenAiCompatibleLlmToolSelectionClient client = new OpenAiCompatibleLlmToolSelectionClient(
                    "",
                    "https://example.invalid/v1",
                    "test-key-not-used",
                    2000,
                    5000,
                    128,
                    0.0d
            );

            LlmToolSelectionClientResponse response = client.completeSelectionPrompt("select a tool");

            assertThat(response.disabled()).isTrue();
            assertThat(requestCount).hasValue(0);
        }
    }

    @Test
    void shouldExtractProviderContentAndKeepItParserCompatible() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        try (StubProvider provider = StubProvider.ok(validProviderResponse(), requestCount, requestBody, authorization)) {
            OpenAiCompatibleLlmToolSelectionClient client = new OpenAiCompatibleLlmToolSelectionClient(
                    "selector-model",
                    provider.baseUrl(),
                    "test-key-not-used",
                    2000,
                    5000,
                    128,
                    0.0d
            );

            LlmToolSelectionClientResponse response = client.completeSelectionPrompt("select a summary tool");
            LlmToolSelectionResult parsed = new LlmToolSelectionParser(Set.of(
                    "document_status_tool",
                    "document_summary_tool",
                    "document_qa_tool"
            )).parse(response.rawText());

            assertThat(response.disabled()).isFalse();
            assertThat(response.provider()).isEqualTo("openai_compatible");
            assertThat(parsed.decision()).isEqualTo("summary_tool");
            assertThat(requestCount).hasValue(1);
            assertThat(requestBody.get()).contains("\"stream\":false");
            assertThat(authorization.get()).startsWith("Bearer ");
        }
    }

    @Test
    void shouldReturnDisabledForNon2xx() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (StubProvider provider = StubProvider.of(500, "{\"error\":\"failed\"}", requestCount)) {
            OpenAiCompatibleLlmToolSelectionClient client = configuredClient(provider.baseUrl());

            LlmToolSelectionClientResponse response = client.completeSelectionPrompt("select");

            assertThat(response.disabled()).isTrue();
            assertThat(response.errorMessage()).contains("provider_http_status=500");
            assertThat(requestCount).hasValue(1);
        }
    }

    @Test
    void shouldReturnDisabledForInvalidProviderJson() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (StubProvider provider = StubProvider.of(200, "not-json", requestCount)) {
            OpenAiCompatibleLlmToolSelectionClient client = configuredClient(provider.baseUrl());

            LlmToolSelectionClientResponse response = client.completeSelectionPrompt("select");

            assertThat(response.disabled()).isTrue();
            assertThat(response.errorMessage()).contains("provider_invalid_response");
        }
    }

    @Test
    void shouldReturnDisabledForEmptyContent() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (StubProvider provider = StubProvider.of(200, "{\"choices\":[{\"message\":{\"content\":\"\"}}]}", requestCount)) {
            OpenAiCompatibleLlmToolSelectionClient client = configuredClient(provider.baseUrl());

            LlmToolSelectionClientResponse response = client.completeSelectionPrompt("select");

            assertThat(response.disabled()).isTrue();
            assertThat(response.errorMessage()).contains("provider_empty_content");
        }
    }

    private OpenAiCompatibleLlmToolSelectionClient configuredClient(String baseUrl) {
        return new OpenAiCompatibleLlmToolSelectionClient(
                "selector-model",
                baseUrl,
                "test-key-not-used",
                2000,
                5000,
                128,
                0.0d
        );
    }

    private static String validProviderResponse() {
        return """
                {"choices":[{"message":{"content":"{\\"decision\\":\\"summary_tool\\",\\"toolNames\\":[\\"document_summary_tool\\"],\\"routingReason\\":\\"summary request\\",\\"matchedKeywords\\":[\\"summary\\"],\\"confidence\\":0.9}"}}]}
                """;
    }

    private record StubProvider(HttpServer server,
                                AtomicInteger requestCount,
                                AtomicReference<String> requestBody,
                                AtomicReference<String> authorization) implements AutoCloseable {

        static StubProvider ok(String responseBody, AtomicInteger requestCount) throws IOException {
            return of(200, responseBody, requestCount);
        }

        static StubProvider ok(String responseBody,
                               AtomicInteger requestCount,
                               AtomicReference<String> requestBody,
                               AtomicReference<String> authorization) throws IOException {
            return of(200, responseBody, requestCount, requestBody, authorization);
        }

        static StubProvider of(int statusCode, String responseBody, AtomicInteger requestCount) throws IOException {
            return of(statusCode, responseBody, requestCount, new AtomicReference<>(), new AtomicReference<>());
        }

        static StubProvider of(int statusCode,
                               String responseBody,
                               AtomicInteger requestCount,
                               AtomicReference<String> requestBody,
                               AtomicReference<String> authorization) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                requestCount.incrementAndGet();
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return new StubProvider(server, requestCount, requestBody, authorization);
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
