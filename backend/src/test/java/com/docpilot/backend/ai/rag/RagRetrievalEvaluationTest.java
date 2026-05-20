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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrievalEvaluationTest {

    private static final String CASES_RESOURCE = "/rag/rag-retrieval-eval-cases.json";
    private static final double MIN_HIT_RATE = 0.60D;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldEvaluateInMemoryRetrievalCases() throws Exception {
        List<RagRetrievalEvaluationCase> cases = loadCases();

        RagRetrievalEvaluationResult result = evaluateInMemory(cases);

        assertThat(result.total()).isEqualTo(cases.size());
        assertThat(result.hitCount()).isGreaterThanOrEqualTo(3);
        assertThat(result.missCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.hitRate()).isGreaterThanOrEqualTo(MIN_HIT_RATE);
        assertThat(result.averageRetrievedCount()).isGreaterThan(0.0D);
        assertThat(result.failedCaseIds()).isEmpty();
        assertThat(result.toString())
                .doesNotContain("Redis cache stores hot session")
                .doesNotContain("RocketMQ outbox dispatches")
                .doesNotContain("MinIO object storage keeps")
                .doesNotContain("prompt")
                .doesNotContain("secret");
    }

    @Test
    void shouldEvaluateQdrantAdapterWithLocalFakeServer() throws Exception {
        startFakeQdrantServer();
        QdrantVectorStore vectorStore = new QdrantVectorStore(qdrantProperties());
        List<RagRetrievalEvaluationCase> cases = List.of(new RagRetrievalEvaluationCase(
                "qdrant-fake-hit",
                "sanitized fake server document",
                "Where is the fake qdrant evidence?",
                "fake-qdrant-marker",
                1,
                true
        ));

        RagRetrievalEvaluationResult result = evaluate(cases, vectorStore);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.hitCount()).isEqualTo(1);
        assertThat(result.hitRate()).isEqualTo(1.0D);
    }

    @Test
    void shouldKeepFailureSummarySanitized() {
        RagRetrievalEvaluationResult result = new RagRetrievalEvaluationResult(
                2,
                1,
                1,
                0.5D,
                1.0D,
                List.of("case-safe-id")
        );

        assertThat(result.safeSummary()).contains("case-safe-id");
        assertThat(result.safeSummary())
                .doesNotContain("documentText")
                .doesNotContain("prompt")
                .doesNotContain("secret");
    }

    private RagRetrievalEvaluationResult evaluateInMemory(List<RagRetrievalEvaluationCase> cases) {
        return evaluate(cases, new InMemoryVectorStore());
    }

    private RagRetrievalEvaluationResult evaluate(List<RagRetrievalEvaluationCase> cases, VectorStore vectorStore) {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel(128);
        RagIndexService indexService = new RagIndexService(
                embeddingModel,
                vectorStore,
                new RagIndexManager(),
                RagEmbeddingProperties.PROVIDER_FAKE,
                "eval_store",
                new RagChunkingPolicy(120, 20, 10)
        );
        RagRetrievalService retrievalService = new RagRetrievalService(embeddingModel, vectorStore);
        int hitCount = 0;
        int missCount = 0;
        int retrievedTotal = 0;
        java.util.ArrayList<String> failedCaseIds = new java.util.ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            RagRetrievalEvaluationCase evalCase = cases.get(i);
            Long documentId = 9000L + i;
            indexService.indexDocument(documentId, RagIndexKey.DEFAULT_VERSION, evalCase.documentText());
            List<VectorSearchResult> hits = evalCase.documentText().isBlank()
                    ? List.of()
                    : retrievalService.retrieveForQuestion(documentId, evalCase.query(), evalCase.topK());
            retrievedTotal += hits.size();
            boolean markerFound = hits.stream()
                    .anyMatch(hit -> hit.chunk().text().contains(evalCase.expectedMarker()));
            if (markerFound) {
                hitCount++;
            } else {
                missCount++;
            }
            if (evalCase.expectedHit() != markerFound) {
                failedCaseIds.add(evalCase.id());
            }
        }
        int total = cases.size();
        double hitRate = total == 0 ? 0.0D : (double) hitCount / total;
        double averageRetrieved = total == 0 ? 0.0D : (double) retrievedTotal / total;
        return new RagRetrievalEvaluationResult(total, hitCount, missCount, hitRate, averageRetrieved,
                failedCaseIds);
    }

    private List<RagRetrievalEvaluationCase> loadCases() throws IOException {
        try (java.io.InputStream inputStream = getClass().getResourceAsStream(CASES_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("RAG retrieval eval cases resource is missing.");
            }
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    private void startFakeQdrantServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/points/search")) {
                sendJson(exchange, 200, """
                        {
                          "result": [
                            {
                              "id": "9000:default:0:hash-qdrant-eval",
                              "score": 0.99,
                              "payload": {
                                "documentId": 9000,
                                "chunkIndex": 0,
                                "text": "fake-qdrant-marker sanitized retrieval text",
                                "metadata": {
                                  "contentHash": "hash-qdrant-eval",
                                  "charStart": "0",
                                  "charEnd": "42",
                                  "source": "fake-qdrant-eval"
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

    private RagVectorStoreProperties.Qdrant qdrantProperties() {
        RagVectorStoreProperties.Qdrant properties = new RagVectorStoreProperties.Qdrant();
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setCollection("docpilot_eval");
        properties.setConnectTimeoutMs(1000);
        properties.setRequestTimeoutMs(3000);
        return properties;
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    record RagRetrievalEvaluationCase(
            String id,
            String documentText,
            String query,
            String expectedMarker,
            int topK,
            boolean expectedHit
    ) {
        RagRetrievalEvaluationCase {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("case id must not be blank");
            }
            documentText = documentText == null ? "" : documentText;
            query = query == null ? "" : query;
            expectedMarker = expectedMarker == null ? "" : expectedMarker;
            topK = Math.max(1, topK);
        }
    }

    record RagRetrievalEvaluationResult(
            int total,
            int hitCount,
            int missCount,
            double hitRate,
            double averageRetrievedCount,
            List<String> failedCaseIds
    ) {
        RagRetrievalEvaluationResult {
            failedCaseIds = failedCaseIds == null ? List.of() : List.copyOf(failedCaseIds);
        }

        String safeSummary() {
            return "total=" + total
                    + ", hitCount=" + hitCount
                    + ", missCount=" + missCount
                    + ", hitRate=" + String.format(java.util.Locale.ROOT, "%.4f", hitRate)
                    + ", averageRetrievedCount=" + String.format(java.util.Locale.ROOT, "%.2f", averageRetrievedCount)
                    + ", failedCaseIds=" + failedCaseIds;
        }
    }
}
