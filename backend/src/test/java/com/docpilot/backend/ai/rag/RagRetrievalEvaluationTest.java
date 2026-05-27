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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrievalEvaluationTest {

    private static final String CASES_RESOURCE = "/rag/rag-retrieval-eval-cases.json";
    private static final double MIN_HIT_RATE = 0.50D;
    private static final Path REPORT_PATH = Path.of("target", "rag-eval", "rag-retrieval-eval-summary.json");

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
        assertThat(result.hitCount()).isGreaterThanOrEqualTo(4);
        assertThat(result.missCount()).isGreaterThanOrEqualTo(3);
        assertThat(result.hitRate()).isGreaterThanOrEqualTo(MIN_HIT_RATE);
        assertThat(result.averageRetrievedCount()).isGreaterThan(0.0D);
        assertThat(result.provider()).isEqualTo("in_memory");
        assertThat(result.embeddingProvider()).isEqualTo(RagEmbeddingProperties.PROVIDER_FAKE);
        assertThat(result.reusedIndexCount()).isEqualTo(1);
        assertThat(result.isolatedDocumentChecks()).isEqualTo(2);
        assertThat(result.failedCaseIds()).isEmpty();
        assertThat(result.caseSummaries()).hasSize(cases.size());
        assertThat(result.caseSummaries())
                .anySatisfy(summary -> assertThat(summary)
                        .containsEntry("id", "empty-document")
                        .containsEntry("expectedHit", false)
                        .containsEntry("retrievedCount", 0)
                        .containsEntry("hit", false)
                        .containsEntry("miss", true)
                        .containsEntry("passed", true))
                .anySatisfy(summary -> assertThat(summary)
                        .containsEntry("id", "same-keyword-wrong-topic")
                        .containsEntry("expectedHit", false)
                        .containsEntry("hit", false)
                        .containsEntry("passed", true))
                .anySatisfy(summary -> assertThat(summary)
                        .containsEntry("id", "topk-over-available-chunks")
                        .containsEntry("expectedHit", true)
                        .containsEntry("expectedMarker", "topk-boundary-marker")
                        .containsEntry("hit", true)
                        .containsEntry("passed", true));
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
        assertThat(result.provider()).isEqualTo("qdrant_fake_server");
    }

    @Test
    void shouldKeepFailureSummarySanitized() {
        RagRetrievalEvaluationResult result = new RagRetrievalEvaluationResult(
                "in_memory",
                RagEmbeddingProperties.PROVIDER_FAKE,
                2,
                1,
                1,
                0.5D,
                1.0D,
                0,
                0,
                List.of("case-safe-id"),
                List.of(Map.of(
                        "id", "case-safe-id",
                        "expectedHit", false,
                        "expectedMarker", "safe-marker",
                        "retrievedCount", 0,
                        "hit", false,
                        "miss", true,
                        "passed", true
                ))
        );

        assertThat(result.safeSummary()).contains("case-safe-id");
        assertThat(result.safeSummary())
                .doesNotContain("documentText")
                .doesNotContain("prompt")
                .doesNotContain("secret");
    }

    @Test
    void shouldWriteSanitizedEvaluationReport() throws Exception {
        RagRetrievalEvaluationResult result = evaluateInMemory(loadCases());

        Files.createDirectories(REPORT_PATH.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), result.safeReport());

        String report = Files.readString(REPORT_PATH, StandardCharsets.UTF_8);
        assertThat(report).contains("\"provider\" : \"in_memory\"");
        assertThat(report).contains("\"embeddingProvider\" : \"fake\"");
        assertThat(report).contains("\"total\" : 8");
        assertThat(report).contains("\"hitRate\"");
        assertThat(report).contains("\"caseSummaries\"");
        assertThat(report).contains("\"same-keyword-wrong-topic\"");
        assertThat(report).contains("\"topk-over-available-chunks\"");
        assertThat(report)
                .doesNotContain("documentText")
                .doesNotContain("Redis cache stores hot session")
                .doesNotContain("RocketMQ outbox dispatches")
                .doesNotContain("MinIO object storage keeps")
                .doesNotContain("prompt")
                .doesNotContain("secret");
    }

    @Test
    void shouldCoverIndexReuseAndDocumentIsolationInEvalPath() {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel(128);
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        RagIndexManager indexManager = new RagIndexManager();
        RagIndexService indexService = new RagIndexService(
                embeddingModel,
                vectorStore,
                indexManager,
                RagEmbeddingProperties.PROVIDER_FAKE,
                RagIndexManager.VECTOR_STORE_IN_MEMORY,
                new RagChunkingPolicy(120, 20, 10)
        );
        RagRetrievalService retrievalService = new RagRetrievalService(embeddingModel, vectorStore);

        RagIndexService.RagIndexResult first = indexService.indexDocument(9100L, "v1",
                "Redis eval marker appears in this document.");
        RagIndexService.RagIndexResult second = indexService.indexDocument(9100L, "v1",
                "Redis eval marker appears in this document.");
        indexService.indexDocument(9101L, "v1", "MinIO isolation marker appears in a different document.");

        List<VectorSearchResult> doc9100Hits = retrievalService.retrieveForQuestion(9100L, "Where is MinIO isolation marker?", 3);
        List<VectorSearchResult> doc9101Hits = retrievalService.retrieveForQuestion(9101L, "Where is MinIO isolation marker?", 3);

        assertThat(first.state().indexReused()).isFalse();
        assertThat(second.state().indexReused()).isTrue();
        assertThat(doc9100Hits).noneMatch(hit -> hit.chunk().text().contains("MinIO isolation marker"));
        assertThat(doc9101Hits).anyMatch(hit -> hit.chunk().text().contains("MinIO isolation marker"));
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
        java.util.ArrayList<Map<String, Object>> caseSummaries = new java.util.ArrayList<>();
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
            caseSummaries.add(Map.of(
                    "id", evalCase.id(),
                    "expectedHit", evalCase.expectedHit(),
                    "expectedMarker", evalCase.expectedMarker(),
                    "retrievedCount", hits.size(),
                    "hit", markerFound,
                    "miss", !markerFound,
                    "passed", evalCase.expectedHit() == markerFound
            ));
        }
        int reusedIndexCount = 0;
        int isolatedDocumentChecks = 0;
        if (vectorStore instanceof InMemoryVectorStore) {
            RagIndexService.RagIndexResult first = indexService.indexDocument(9900L, "v1",
                    "Cache reuse marker stays stable.");
            RagIndexService.RagIndexResult second = indexService.indexDocument(9900L, "v1",
                    "Cache reuse marker stays stable.");
            indexService.indexDocument(9901L, "v1", "Isolation marker belongs to a separate document.");
            List<VectorSearchResult> isolatedHits = retrievalService.retrieveForQuestion(9900L,
                    "Where is the isolation marker?", 3);
            List<VectorSearchResult> separateDocumentHits = retrievalService.retrieveForQuestion(9901L,
                    "Where is the isolation marker?", 3);
            reusedIndexCount = second.state().indexReused() && !first.state().indexReused() ? 1 : 0;
            isolatedDocumentChecks += isolatedHits.stream()
                    .noneMatch(hit -> hit.chunk().text().contains("Isolation marker")) ? 1 : 0;
            isolatedDocumentChecks += separateDocumentHits.stream()
                    .anyMatch(hit -> hit.chunk().text().contains("Isolation marker")) ? 1 : 0;
        }
        int total = cases.size();
        double hitRate = total == 0 ? 0.0D : (double) hitCount / total;
        double averageRetrieved = total == 0 ? 0.0D : (double) retrievedTotal / total;
        return new RagRetrievalEvaluationResult(
                vectorStore instanceof QdrantVectorStore ? "qdrant_fake_server" : "in_memory",
                RagEmbeddingProperties.PROVIDER_FAKE,
                total,
                hitCount,
                missCount,
                hitRate,
                averageRetrieved,
                reusedIndexCount,
                isolatedDocumentChecks,
                failedCaseIds,
                caseSummaries);
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
            String provider,
            String embeddingProvider,
            int total,
            int hitCount,
            int missCount,
            double hitRate,
            double averageRetrievedCount,
            int reusedIndexCount,
            int isolatedDocumentChecks,
            List<String> failedCaseIds,
            List<Map<String, Object>> caseSummaries
    ) {
        RagRetrievalEvaluationResult {
            provider = provider == null || provider.isBlank() ? "unknown" : provider.trim();
            embeddingProvider = embeddingProvider == null || embeddingProvider.isBlank() ? "unknown" : embeddingProvider.trim();
            failedCaseIds = failedCaseIds == null ? List.of() : List.copyOf(failedCaseIds);
            caseSummaries = caseSummaries == null ? List.of() : List.copyOf(caseSummaries);
        }

        Map<String, Object> safeReport() {
            Map<String, Object> report = new java.util.LinkedHashMap<>();
            report.put("provider", provider);
            report.put("embeddingProvider", embeddingProvider);
            report.put("total", total);
            report.put("hitCount", hitCount);
            report.put("missCount", missCount);
            report.put("hitRate", String.format(java.util.Locale.ROOT, "%.4f", hitRate));
            report.put("averageRetrievedCount", String.format(java.util.Locale.ROOT, "%.2f", averageRetrievedCount));
            report.put("reusedIndexCount", reusedIndexCount);
            report.put("isolatedDocumentChecks", isolatedDocumentChecks);
            report.put("failedCaseIds", failedCaseIds);
            report.put("caseSummaries", caseSummaries);
            return report;
        }

        String safeSummary() {
            return "provider=" + provider
                    + ", embeddingProvider=" + embeddingProvider
                    + ", total=" + total
                    + ", hitCount=" + hitCount
                    + ", missCount=" + missCount
                    + ", hitRate=" + String.format(java.util.Locale.ROOT, "%.4f", hitRate)
                    + ", averageRetrievedCount=" + String.format(java.util.Locale.ROOT, "%.2f", averageRetrievedCount)
                    + ", failedCaseIds=" + failedCaseIds;
        }
    }
}
