package com.docpilot.backend.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagVectorStoreOfflineDemoTest {

    private static final Path REPORT_PATH = Path.of("target", "rag-demo",
            "rag-vector-store-offline-demo-summary.json");
    private static final String PRIVATE_DOC_MARKER = "PRIVATE_OFFLINE_DEMO_DOC_MARKER";
    private static final String PRIVATE_QUERY_MARKER = "PRIVATE_OFFLINE_DEMO_QUERY_MARKER";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private int upsertRequests;
    private int searchRequests;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldWriteSanitizedOfflineDemoReport() throws Exception {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel(32);
        EmbeddingVector firstEmbedding = embeddingModel.embed("Redis cache marker " + PRIVATE_QUERY_MARKER);
        EmbeddingVector secondEmbedding = embeddingModel.embed("Redis cache marker " + PRIVATE_QUERY_MARKER);
        InMemoryVectorStore inMemoryVectorStore = new InMemoryVectorStore();
        DocumentChunk inMemoryChunk = new DocumentChunk(7001L, 0,
                "Redis cache marker appears in synthetic offline demo text. " + PRIVATE_DOC_MARKER,
                Map.of("contentHash", "offline-hash-0", "charStart", "0", "charEnd", "72"));

        inMemoryVectorStore.add(RagSearchScope.of("offline-user", 7001L), inMemoryChunk,
                embeddingModel.embed(inMemoryChunk.text()));
        int inMemoryTopK = 2;
        List<VectorSearchResult> inMemoryHits = inMemoryVectorStore.searchTopK(
                RagSearchScope.of("offline-user", 7001L),
                embeddingModel.embed("Where is the Redis cache marker?"),
                inMemoryTopK
        );

        startFakeQdrantServer(false);
        QdrantVectorStore qdrantVectorStore = new QdrantVectorStore(qdrantProperties("docpilot_offline_demo"));
        DocumentChunk qdrantChunk = new DocumentChunk(7002L, 0,
                "Qdrant fake server marker appears in synthetic offline demo text.",
                Map.of("contentHash", "offline-qdrant-hash", "charStart", "0", "charEnd", "64"));
        qdrantVectorStore.add(RagSearchScope.of("offline-user", 7002L), qdrantChunk,
                embeddingModel.embed(qdrantChunk.text()));
        int qdrantTopK = 2;
        List<VectorSearchResult> qdrantHits = qdrantVectorStore.searchTopK(
                RagSearchScope.of("offline-user", 7002L),
                embeddingModel.embed("Where is the fake server marker?"),
                qdrantTopK
        );

        String fallbackReason = qdrantFallbackReason();
        List<Map<String, Object>> retrievalSummaries = List.of(
                retrievalSummary("in-memory-smoke", "in_memory", 7001L, "cache-marker-query",
                        inMemoryTopK, inMemoryHits, false, "none"),
                retrievalSummary("qdrant-fake-server-smoke", "qdrant_fake_server", 7002L,
                        "fake-server-marker-query", qdrantTopK, qdrantHits, false, "none"),
                retrievalSummary("qdrant-fallback-smoke", "qdrant_fake_server", 7002L,
                        "qdrant-fallback-query", 1, List.of(), true, fallbackReason)
        );
        Map<String, Object> safeReport = Map.ofEntries(
                Map.entry("mode", "offline"),
                Map.entry("embeddingProvider", RagEmbeddingProperties.PROVIDER_FAKE),
                Map.entry("embeddingStable", firstEmbedding.equals(secondEmbedding)),
                Map.entry("embeddingDimension", firstEmbedding.dimension()),
                Map.entry("vectorStoreProvider", "in_memory"),
                Map.entry("inMemoryIndexedChunks", inMemoryVectorStore.size()),
                Map.entry("inMemoryRetrievedCount", inMemoryHits.size()),
                Map.entry("inMemoryTopHitDocumentIdPresent", !inMemoryHits.isEmpty()
                        && inMemoryHits.get(0).chunk().documentId() != null),
                Map.entry("qdrantMode", "fake_server"),
                Map.entry("qdrantUpsertObserved", upsertRequests > 0),
                Map.entry("qdrantSearchObserved", searchRequests > 0),
                Map.entry("qdrantRetrievedCount", qdrantHits.size()),
                Map.entry("qdrantFallbackChecked", true),
                Map.entry("qdrantFallbackReason", fallbackReason),
                Map.entry("retrievalSummaries", retrievalSummaries),
                Map.entry("sanitized", true)
        );

        Files.createDirectories(REPORT_PATH.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), safeReport);

        String report = Files.readString(REPORT_PATH, StandardCharsets.UTF_8);
        assertThat(safeReport)
                .containsEntry("embeddingStable", true)
                .containsEntry("embeddingDimension", 32)
                .containsEntry("inMemoryRetrievedCount", 1)
                .containsEntry("qdrantUpsertObserved", true)
                .containsEntry("qdrantSearchObserved", true)
                .containsEntry("qdrantRetrievedCount", 1)
                .containsEntry("qdrantFallbackReason", "qdrant_http_error");
        assertThat(retrievalSummaries).hasSize(3);
        assertThat(retrievalSummaries.get(0))
                .containsEntry("vectorStoreType", "in_memory")
                .containsEntry("embeddingProvider", RagEmbeddingProperties.PROVIDER_FAKE)
                .containsEntry("documentId", 7001L)
                .containsEntry("query", "cache-marker-query")
                .containsEntry("topK", 2)
                .containsEntry("retrievedCount", 1)
                .containsEntry("fallbackUsed", false)
                .containsEntry("fallbackReason", "none")
                .containsEntry("contextHashPresent", true);
        assertThat(retrievalSummaries.get(1))
                .containsEntry("vectorStoreType", "qdrant_fake_server")
                .containsEntry("retrievedCount", 1)
                .containsEntry("fallbackUsed", false);
        assertThat(retrievalSummaries.get(2))
                .containsEntry("fallbackUsed", true)
                .containsEntry("fallbackReason", "qdrant_http_error")
                .containsEntry("retrievedCount", 0)
                .containsEntry("contextHashPresent", false);
        assertThat(report)
                .doesNotContain(PRIVATE_DOC_MARKER)
                .doesNotContain(PRIVATE_QUERY_MARKER)
                .doesNotContain("Redis cache marker appears")
                .doesNotContain("Qdrant fake server marker appears")
                .doesNotContain("fake-qdrant-offline-hit")
                .doesNotContain("Authorization")
                .doesNotContain("apiKey")
                .doesNotContain("provider response")
                .doesNotContain("documentText")
                .doesNotContain("prompt")
                .doesNotContain("127.0.0.1");
    }

    @Test
    void shouldClassifyQdrantFallbackWithoutLeakingFailureDetails() throws Exception {
        startFakeQdrantServer(true);
        QdrantVectorStore vectorStore = new QdrantVectorStore(qdrantProperties("docpilot_offline_fallback"));

        assertThatThrownBy(() -> vectorStore.searchTopK(
                RagSearchScope.of("offline-user", 7003L),
                new FakeEmbeddingModel(32).embed("fallback query"),
                1
        )).satisfies(throwable -> {
            assertThat(RagFallbackReasonClassifier.classify(throwable)).isEqualTo("qdrant_http_error");
            assertThat(throwable.getMessage())
                    .doesNotContain("127.0.0.1")
                    .doesNotContain("offline-user")
                    .doesNotContain("fallback query")
                    .doesNotContain("provider response");
        });
    }

    private Map<String, Object> retrievalSummary(String sampleId,
                                                 String vectorStoreType,
                                                 Long documentId,
                                                 String queryLabel,
                                                 int topK,
                                                 List<VectorSearchResult> hits,
                                                 boolean fallbackUsed,
                                                 String fallbackReason) {
        return Map.ofEntries(
                Map.entry("sampleId", sampleId),
                Map.entry("vectorStoreType", vectorStoreType),
                Map.entry("embeddingProvider", RagEmbeddingProperties.PROVIDER_FAKE),
                Map.entry("documentId", documentId),
                Map.entry("query", queryLabel),
                Map.entry("topK", topK),
                Map.entry("retrievedCount", hits.size()),
                Map.entry("scoreSummary", scoreSummary(hits)),
                Map.entry("citationMetadataSummary", citationMetadataSummary(hits)),
                Map.entry("fallbackUsed", fallbackUsed),
                Map.entry("fallbackReason", fallbackReason),
                Map.entry("contextHashPresent", hits.stream()
                        .anyMatch(hit -> hasMetadata(hit, "contentHash")))
        );
    }

    private Map<String, Object> scoreSummary(List<VectorSearchResult> hits) {
        if (hits.isEmpty()) {
            return Map.of("count", 0, "min", 0.0d, "max", 0.0d, "average", 0.0d);
        }
        double min = hits.stream().mapToDouble(VectorSearchResult::score).min().orElse(0.0d);
        double max = hits.stream().mapToDouble(VectorSearchResult::score).max().orElse(0.0d);
        double average = hits.stream().mapToDouble(VectorSearchResult::score).average().orElse(0.0d);
        return Map.of(
                "count", hits.size(),
                "min", roundScore(min),
                "max", roundScore(max),
                "average", roundScore(average)
        );
    }

    private List<Map<String, Object>> citationMetadataSummary(List<VectorSearchResult> hits) {
        return hits.stream()
                .map(hit -> {
                    Map<String, Object> summary = Map.ofEntries(
                            Map.entry("documentIdPresent", hit.chunk().documentId() != null),
                            Map.entry("chunkIndex", hit.chunk().chunkIndex()),
                            Map.entry("contentHashPresent", hasMetadata(hit, "contentHash")),
                            Map.entry("charStartPresent", hasMetadata(hit, "charStart")),
                            Map.entry("charEndPresent", hasMetadata(hit, "charEnd")),
                            Map.entry("sourcePresent", hasMetadata(hit, "source"))
                    );
                    return summary;
                })
                .toList();
    }

    private boolean hasMetadata(VectorSearchResult hit, String key) {
        String value = hit.chunk().metadata().get(key);
        return value != null && !value.isBlank();
    }

    private double roundScore(double value) {
        return Math.round(value * 10000.0d) / 10000.0d;
    }

    private String qdrantFallbackReason() throws IOException {
        server.stop(0);
        server = null;
        startFakeQdrantServer(true);
        QdrantVectorStore failingStore = new QdrantVectorStore(qdrantProperties("docpilot_offline_fallback"));
        try {
            failingStore.searchTopK(
                    RagSearchScope.of("offline-user", 7002L),
                    new FakeEmbeddingModel(32).embed("fallback query " + PRIVATE_QUERY_MARKER),
                    1
            );
            return "not_triggered";
        } catch (RuntimeException ex) {
            return RagFallbackReasonClassifier.classify(ex);
        }
    }

    private void startFakeQdrantServer(boolean failSearch) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/points/search")) {
                searchRequests++;
                if (failSearch) {
                    sendJson(exchange, 500, "{\"error\":\"do-not-print\"}");
                    return;
                }
                sendJson(exchange, 200, """
                        {
                          "result": [
                            {
                              "id": "7002:default:0:offline-qdrant-hash",
                              "score": 0.97,
                              "payload": {
                                "documentId": 7002,
                                "chunkIndex": 0,
                                "text": "fake-qdrant-offline-hit",
                                "metadata": {
                                  "contentHash": "offline-qdrant-hash",
                                  "charStart": "0",
                                  "charEnd": "64",
                                  "source": "offline-demo"
                                }
                              }
                            }
                          ]
                        }
                        """);
                return;
            }
            upsertRequests++;
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        });
        server.start();
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private RagVectorStoreProperties.Qdrant qdrantProperties(String collection) {
        RagVectorStoreProperties.Qdrant properties = new RagVectorStoreProperties.Qdrant();
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setCollection(collection);
        properties.setConnectTimeoutMs(1000);
        properties.setRequestTimeoutMs(3000);
        return properties;
    }
}
