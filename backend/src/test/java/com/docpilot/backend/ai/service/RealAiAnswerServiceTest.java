package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.exception.AiNonRetryableException;
import com.docpilot.backend.ai.exception.AiRetryableException;
import com.docpilot.backend.ai.service.impl.RealAiAnswerService;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealAiAnswerServiceTest {

    @Test
    void shouldCallOpenAiCompatibleEndpointWithExpectedRequestContract() throws IOException {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] responseBytes = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        });
        server.start();

        try {
            RealAiAnswerService service = buildService(server, 1000);
            String answer = service.answer("文档内容", "请总结");

            assertEquals("ok", answer);
            assertEquals("/chat/completions", requestPath.get());
            assertEquals("Bearer test-key", authorizationHeader.get());
            assertTrue(requestBody.get().contains("\"model\":\"test-model\""));
            assertTrue(requestBody.get().contains("\"temperature\":0.1"));
            assertTrue(requestBody.get().contains("\"max_tokens\":128"));
            assertTrue(requestBody.get().contains("\"messages\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnAnswerAndRecordTokenAndCostWhenUsageProvided() throws IOException {
        HttpServer server = startServer(200, """
                {
                  "choices": [{"message": {"content": "真实回答"}}],
                  "usage": {"prompt_tokens": 50, "completion_tokens": 20, "total_tokens": 70}
                }
                """);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
        try {
            RealAiAnswerService service = buildService(server, 1000);

            String answer = service.answer("文档内容", "问题");

            assertEquals("真实回答", answer);
            DistributionSummary tokenSummary = registry.find("docpilot.ai.token.usage")
                    .tags("scene", "qa.model", "type", "total")
                    .summary();
            DistributionSummary costSummary = registry.find("docpilot.ai.cost.estimate")
                    .tags("scene", "qa.model", "currency", "usd")
                    .summary();
            assertTrue(tokenSummary != null && tokenSummary.count() == 1);
            assertTrue(costSummary != null && costSummary.count() == 1);
        } finally {
            Metrics.removeRegistry(registry);
            registry.close();
            server.stop(0);
        }
    }

    @Test
    void shouldThrowRetryableWhenStatusIs503() throws IOException {
        HttpServer server = startServer(503, "{\"error\":\"service unavailable\"}");
        try {
            RealAiAnswerService service = buildService(server, 1000);
            assertThrows(AiRetryableException.class, () -> service.answer("ctx", "q"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldThrowNonRetryableWhenStatusIs400() throws IOException {
        HttpServer server = startServer(400, "{\"error\":\"bad request\"}");
        try {
            RealAiAnswerService service = buildService(server, 1000);
            assertThrows(AiNonRetryableException.class, () -> service.answer("ctx", "q"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldThrowNonRetryableWhenStatusIs401() throws IOException {
        HttpServer server = startServer(401, "{\"error\":{\"message\":\"invalid api key\"}}");
        try {
            RealAiAnswerService service = buildService(server, 1000);
            AiNonRetryableException exception = assertThrows(AiNonRetryableException.class, () -> service.answer("ctx", "q"));
            assertTrue(exception.getMessage().contains("status=401"));
            assertTrue(exception.getMessage().contains("鉴权失败"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldThrowRetryableWhenStatusIs429() throws IOException {
        HttpServer server = startServer(429, "{\"error\":{\"message\":\"rate limit exceeded\"}}");
        try {
            RealAiAnswerService service = buildService(server, 1000);
            AiRetryableException exception = assertThrows(AiRetryableException.class, () -> service.answer("ctx", "q"));
            assertTrue(exception.getMessage().contains("status=429"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldThrowRetryableWhenRequestTimesOut() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                Thread.sleep(150);
                byte[] responseBytes = "{\"choices\":[{\"message\":{\"content\":\"late\"}}]}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBytes);
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();

        try {
            RealAiAnswerService service = buildService(server, 50);
            assertThrows(AiRetryableException.class, () -> service.answer("ctx", "q"));
        } finally {
            server.stop(0);
        }
    }

    private RealAiAnswerService buildService(HttpServer server, int readTimeoutMs) {
        RealAiAnswerService service = new RealAiAnswerService();
        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 1000);
        ReflectionTestUtils.setField(service, "readTimeoutMs", readTimeoutMs);
        ReflectionTestUtils.setField(service, "temperature", 0.1D);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 128);
        ReflectionTestUtils.setField(service, "inputCostPer1kUsd", 0.01D);
        ReflectionTestUtils.setField(service, "outputCostPer1kUsd", 0.03D);
        return service;
    }

    private HttpServer startServer(int statusCode, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        });
        server.start();
        return server;
    }
}

