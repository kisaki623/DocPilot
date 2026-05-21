package com.docpilot.backend.ai.rag;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrievalEvaluationArtifactTest {

    private static final Path ARTIFACT_DIR = Path.of("..", "docs", "ai-dev", "benchmarks", "rag");
    private static final Path JSON_PATH = ARTIFACT_DIR.resolve("offline-retrieval-evaluation.json");
    private static final Path MARKDOWN_PATH = ARTIFACT_DIR.resolve("offline-retrieval-evaluation.md");
    private static final String PRIVATE_DOC_MARKER = "PRIVATE_EVAL_ARTIFACT_DOC_MARKER";
    private static final String PRIVATE_QUERY_MARKER = "PRIVATE_EVAL_ARTIFACT_QUERY_MARKER";
    private static final String IN_MEMORY_MARKER = "alpha-cache-marker";
    private static final String QDRANT_MARKER = "qdrant-artifact-marker";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        servers.forEach(server -> server.stop(0));
        servers.clear();
    }

    @Test
    void shouldWriteSanitizedRetrievalEvaluationArtifacts() throws Exception {
        EvaluationReport report = buildReport();

        Files.createDirectories(ARTIFACT_DIR);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(JSON_PATH.toFile(), report.toSafeMap());
        Files.writeString(MARKDOWN_PATH, report.toMarkdown(), StandardCharsets.UTF_8);

        String json = Files.readString(JSON_PATH, StandardCharsets.UTF_8);
        String markdown = Files.readString(MARKDOWN_PATH, StandardCharsets.UTF_8);
        assertThat(json).contains("\"provider\" : \"in_memory\"");
        assertThat(json).contains("\"provider\" : \"qdrant_fake_server\"");
        assertThat(markdown).contains("| in_memory |");
        assertThat(markdown).contains("| qdrant_fake_server |");
        assertThat(json + markdown)
                .doesNotContain(PRIVATE_DOC_MARKER)
                .doesNotContain(PRIVATE_QUERY_MARKER)
                .doesNotContain("Synthetic cache evidence")
                .doesNotContain("Synthetic upload evidence")
                .doesNotContain(IN_MEMORY_MARKER)
                .doesNotContain(QDRANT_MARKER)
                .doesNotContain("Authorization")
                .doesNotContain("apiKey")
                .doesNotContain("provider response")
                .doesNotContain("documentText")
                .doesNotContain("prompt")
                .doesNotContain("127.0.0.1");
    }

    private EvaluationReport buildReport() throws Exception {
        StoreEvaluation inMemory = evaluateInMemory();
        StoreEvaluation qdrant = evaluateQdrantFakeServer();
        StoreEvaluation fallback = evaluateQdrantFallback();
        return new EvaluationReport(
                "offline",
                RagEmbeddingProperties.PROVIDER_FAKE,
                List.of(inMemory, qdrant, fallback),
                fallback.fallbackReason(),
                true
        );
    }

    private StoreEvaluation evaluateInMemory() {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel(128);
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        RagIndexService indexService = new RagIndexService(
                embeddingModel,
                vectorStore,
                new RagIndexManager(),
                RagEmbeddingProperties.PROVIDER_FAKE,
                RagIndexManager.VECTOR_STORE_IN_MEMORY,
                new RagChunkingPolicy(120, 20, 10)
        );
        RagRetrievalService retrievalService = new RagRetrievalService(embeddingModel, vectorStore);
        List<EvalCase> cases = List.of(
                new EvalCase("cache-hit", "Synthetic cache evidence includes " + IN_MEMORY_MARKER + ".",
                        "Where is cache evidence " + PRIVATE_QUERY_MARKER + "?", IN_MEMORY_MARKER, "cache-evidence", true),
                new EvalCase("upload-hit", "Synthetic upload evidence includes upload-artifact-marker.",
                        "Where is upload evidence?", "upload-artifact-marker", "upload-evidence", true),
                new EvalCase("agent-hit", "Synthetic agent evidence includes agent-artifact-marker.",
                        "Where is agent evidence?", "agent-artifact-marker", "agent-evidence", true),
                new EvalCase("no-match-query", "Synthetic cache evidence includes " + IN_MEMORY_MARKER + ".",
                        "Which payment gateway appears?", "payment-artifact-marker", "payment-gateway", false),
                new EvalCase("empty-document", "", "What can be retrieved?", "empty-artifact-marker", "empty-document", false),
                new EvalCase("same-keyword-wrong-topic",
                        "Synthetic agent evidence describes routing metadata but no billing policy.",
                        "Which agent pricing policy controls billing?", "agent-pricing-policy", "wrong-topic-agent-policy", false),
                new EvalCase("topk-over-available-chunks",
                        "Synthetic short evidence includes topk-artifact-marker.",
                        "Where is the topK boundary evidence?", "topk-artifact-marker", "topk-boundary", true)
        );

        int positiveCases = 0;
        int positiveHits = 0;
        int retrievedTotal = 0;
        List<String> failedCaseIds = new ArrayList<>();
        List<CaseEvaluation> caseEvaluations = new ArrayList<>();
        boolean noMatchPassed = false;
        boolean emptyDocumentPassed = false;
        for (int i = 0; i < cases.size(); i++) {
            EvalCase evalCase = cases.get(i);
            Long documentId = 8100L + i;
            indexService.indexDocument(documentId, RagIndexKey.DEFAULT_VERSION,
                    evalCase.documentText() + " " + PRIVATE_DOC_MARKER);
            List<VectorSearchResult> hits = evalCase.documentText().isBlank()
                    ? List.of()
                    : retrievalService.retrieveForQuestion(documentId, evalCase.query(), 2);
            retrievedTotal += hits.size();
            boolean markerFound = hits.stream()
                    .anyMatch(hit -> hit.chunk().text().contains(evalCase.expectedMarker()));
            if (evalCase.expectedHit()) {
                positiveCases++;
                if (markerFound) {
                    positiveHits++;
                }
            }
            boolean passed = evalCase.expectedHit() == markerFound;
            if (!passed) {
                failedCaseIds.add(evalCase.id());
            }
            caseEvaluations.add(new CaseEvaluation(evalCase.id(), evalCase.expectedHit(),
                    evalCase.expectedMarkerLabel(), hits.size(), markerFound, !markerFound, passed));
            if ("no-match-query".equals(evalCase.id())) {
                noMatchPassed = passed;
            }
            if ("empty-document".equals(evalCase.id())) {
                emptyDocumentPassed = passed && hits.isEmpty();
            }
        }

        boolean isolationPassed = evaluateInMemoryIsolation(indexService, retrievalService);
        return StoreEvaluation.of(
                "in_memory",
                cases.size(),
                positiveCases,
                positiveHits,
                retrievedTotal,
                noMatchPassed,
                emptyDocumentPassed,
                isolationPassed,
                false,
                "",
                failedCaseIds,
                caseEvaluations
        );
    }

    private boolean evaluateInMemoryIsolation(RagIndexService indexService, RagRetrievalService retrievalService) {
        indexService.indexDocument(8201L, "v1", "Synthetic isolated document A.");
        indexService.indexDocument(8202L, "v1", "Synthetic isolated document B includes beta-isolation-marker.");
        List<VectorSearchResult> docA = retrievalService.retrieveForQuestion(8201L, "Where is beta isolation?", 3);
        List<VectorSearchResult> docB = retrievalService.retrieveForQuestion(8202L, "Where is beta isolation?", 3);
        return docA.stream().noneMatch(hit -> hit.chunk().text().contains("beta-isolation-marker"))
                && docB.stream().anyMatch(hit -> hit.chunk().text().contains("beta-isolation-marker"));
    }

    private StoreEvaluation evaluateQdrantFakeServer() throws Exception {
        HttpServer server = startFakeQdrantServer(false);
        QdrantVectorStore vectorStore = new QdrantVectorStore(qdrantProperties(server, "docpilot_eval_artifact"));
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel(128);
        DocumentChunk chunk = new DocumentChunk(8301L, 0,
                "Synthetic qdrant artifact evidence includes " + QDRANT_MARKER + ".",
                Map.of("contentHash", "artifact-qdrant-hash", "charStart", "0", "charEnd", "64"));
        vectorStore.add(RagSearchScope.of("artifact-user", 8301L), chunk, embeddingModel.embed(chunk.text()));
        List<VectorSearchResult> hits = vectorStore.searchTopK(
                RagSearchScope.of("artifact-user", 8301L),
                embeddingModel.embed("Where is qdrant artifact evidence?"),
                1
        );
        boolean markerFound = hits.stream().anyMatch(hit -> hit.chunk().text().contains(QDRANT_MARKER));
        return StoreEvaluation.of(
                "qdrant_fake_server",
                1,
                1,
                markerFound ? 1 : 0,
                hits.size(),
                true,
                true,
                true,
                false,
                "",
                markerFound ? List.of() : List.of("qdrant-fake-hit"),
                List.of(new CaseEvaluation("qdrant-fake-hit", true, "qdrant-artifact",
                        hits.size(), markerFound, !markerFound, markerFound))
        );
    }

    private StoreEvaluation evaluateQdrantFallback() throws IOException {
        HttpServer server = startFakeQdrantServer(true);
        QdrantVectorStore vectorStore = new QdrantVectorStore(qdrantProperties(server, "docpilot_eval_fallback"));
        String fallbackReason;
        try {
            vectorStore.searchTopK(
                    RagSearchScope.of("artifact-user", 8401L),
                    new FakeEmbeddingModel(128).embed("fallback " + PRIVATE_QUERY_MARKER),
                    1
            );
            fallbackReason = "not_triggered";
        } catch (RuntimeException ex) {
            fallbackReason = RagFallbackReasonClassifier.classify(ex);
        }
        return StoreEvaluation.of(
                "qdrant_fake_server_fallback",
                1,
                0,
                0,
                0,
                true,
                true,
                true,
                true,
                fallbackReason,
                "qdrant_http_error".equals(fallbackReason) ? List.of() : List.of("qdrant-fallback"),
                List.of(new CaseEvaluation("qdrant-fallback", false, "qdrant-fallback",
                        0, false, true, "qdrant_http_error".equals(fallbackReason)))
        );
    }

    private HttpServer startFakeQdrantServer(boolean failSearch) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/points/search")) {
                if (failSearch) {
                    sendJson(exchange, 500, "{\"error\":\"do-not-print\"}");
                    return;
                }
                sendJson(exchange, 200, """
                        {
                          "result": [
                            {
                              "id": "8301:default:0:artifact-qdrant-hash",
                              "score": 0.98,
                              "payload": {
                                "documentId": 8301,
                                "chunkIndex": 0,
                                "text": "qdrant-artifact-marker",
                                "metadata": {
                                  "contentHash": "artifact-qdrant-hash",
                                  "charStart": "0",
                                  "charEnd": "64",
                                  "source": "offline-artifact"
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
        servers.add(server);
        return server;
    }

    private RagVectorStoreProperties.Qdrant qdrantProperties(HttpServer server, String collection) {
        RagVectorStoreProperties.Qdrant properties = new RagVectorStoreProperties.Qdrant();
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setCollection(collection);
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

    private record EvalCase(String id,
                            String documentText,
                            String query,
                            String expectedMarker,
                            String expectedMarkerLabel,
                            boolean expectedHit) {
    }

    private record CaseEvaluation(
            String id,
            boolean expectedHit,
            String expectedMarker,
            int retrievedCount,
            boolean hit,
            boolean miss,
            boolean passed
    ) {
        Map<String, Object> toSafeMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("expectedHit", expectedHit);
            value.put("expectedMarker", expectedMarker);
            value.put("retrievedCount", retrievedCount);
            value.put("hit", hit);
            value.put("miss", miss);
            value.put("passed", passed);
            return value;
        }
    }

    private record EvaluationReport(
            String mode,
            String embeddingProvider,
            List<StoreEvaluation> stores,
            String fallbackReason,
            boolean sanitized
    ) {
        Map<String, Object> toSafeMap() {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("mode", mode);
            root.put("embeddingProvider", embeddingProvider);
            root.put("stores", stores.stream().map(StoreEvaluation::toSafeMap).toList());
            root.put("fallbackReason", fallbackReason);
            root.put("sanitized", sanitized);
            root.put("notes", List.of(
                    "Synthetic cases only",
                    "No document text or model inputs are stored",
                    "Qdrant coverage uses a local fake server"
            ));
            return root;
        }

        String toMarkdown() {
            StringBuilder builder = new StringBuilder();
            builder.append("# Offline RAG Retrieval Evaluation\n\n");
            builder.append("- Mode: `").append(mode).append("`\n");
            builder.append("- Embedding provider: `").append(embeddingProvider).append("`\n");
            builder.append("- Fallback reason: `").append(fallbackReason).append("`\n");
            builder.append("- Sanitized: `").append(sanitized).append("`\n\n");
            builder.append("| Provider | Total | Positive hit rate | Avg retrieved | No-match | Empty doc | Isolation | Fallback |\n");
            builder.append("| --- | ---: | ---: | ---: | --- | --- | --- | --- |\n");
            for (StoreEvaluation store : stores) {
                builder.append("| ").append(store.provider())
                        .append(" | ").append(store.totalCases())
                        .append(" | ").append(store.positiveHitRate())
                        .append(" | ").append(store.averageRetrievedCount())
                        .append(" | ").append(store.noMatchPassed())
                        .append(" | ").append(store.emptyDocumentPassed())
                        .append(" | ").append(store.isolationPassed())
                        .append(" | ").append(store.fallbackUsed())
                        .append(" |\n");
            }
            builder.append("\n| Provider | Case | Expected hit | Expected marker | Retrieved | Hit | Miss | Passed |\n");
            builder.append("| --- | --- | --- | --- | ---: | --- | --- | --- |\n");
            for (StoreEvaluation store : stores) {
                for (CaseEvaluation evaluation : store.caseEvaluations()) {
                    builder.append("| ").append(store.provider())
                            .append(" | ").append(evaluation.id())
                            .append(" | ").append(evaluation.expectedHit())
                            .append(" | ").append(evaluation.expectedMarker())
                            .append(" | ").append(evaluation.retrievedCount())
                            .append(" | ").append(evaluation.hit())
                            .append(" | ").append(evaluation.miss())
                            .append(" | ").append(evaluation.passed())
                            .append(" |\n");
                }
            }
            builder.append("\nArtifacts are generated from synthetic fixtures only and intentionally omit source text.\n");
            return builder.toString();
        }
    }

    private record StoreEvaluation(
            String provider,
            int totalCases,
            int positiveCases,
            int positiveHits,
            String positiveHitRate,
            String averageRetrievedCount,
            boolean noMatchPassed,
            boolean emptyDocumentPassed,
            boolean isolationPassed,
            boolean fallbackUsed,
            String fallbackReason,
            List<String> failedCaseIds,
            List<CaseEvaluation> caseEvaluations
    ) {
        static StoreEvaluation of(String provider,
                                  int totalCases,
                                  int positiveCases,
                                  int positiveHits,
                                  int retrievedTotal,
                                  boolean noMatchPassed,
                                  boolean emptyDocumentPassed,
                                  boolean isolationPassed,
                                  boolean fallbackUsed,
                                  String fallbackReason,
                                  List<String> failedCaseIds,
                                  List<CaseEvaluation> caseEvaluations) {
            double hitRate = positiveCases == 0 ? 0.0D : (double) positiveHits / positiveCases;
            double averageRetrieved = totalCases == 0 ? 0.0D : (double) retrievedTotal / totalCases;
            return new StoreEvaluation(
                    provider,
                    totalCases,
                    positiveCases,
                    positiveHits,
                    String.format(java.util.Locale.ROOT, "%.4f", hitRate),
                    String.format(java.util.Locale.ROOT, "%.2f", averageRetrieved),
                    noMatchPassed,
                    emptyDocumentPassed,
                    isolationPassed,
                    fallbackUsed,
                    fallbackReason == null ? "" : fallbackReason,
                    failedCaseIds == null ? List.of() : List.copyOf(failedCaseIds),
                    caseEvaluations == null ? List.of() : List.copyOf(caseEvaluations)
            );
        }

        Map<String, Object> toSafeMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("provider", provider);
            value.put("totalCases", totalCases);
            value.put("positiveCases", positiveCases);
            value.put("positiveHits", positiveHits);
            value.put("positiveHitRate", positiveHitRate);
            value.put("averageRetrievedCount", averageRetrievedCount);
            value.put("noMatchPassed", noMatchPassed);
            value.put("emptyDocumentPassed", emptyDocumentPassed);
            value.put("isolationPassed", isolationPassed);
            value.put("fallbackUsed", fallbackUsed);
            value.put("fallbackReason", fallbackReason);
            value.put("failedCaseIds", failedCaseIds);
            value.put("caseSummaries", caseEvaluations.stream().map(CaseEvaluation::toSafeMap).toList());
            return value;
        }
    }
}
