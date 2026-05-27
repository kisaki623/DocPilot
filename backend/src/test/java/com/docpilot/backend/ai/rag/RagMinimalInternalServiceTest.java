package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMinimalInternalServiceTest {

    @Test
    void shouldSplitDocumentIntoChunksWithMetadata() {
        RagIndexService indexService = new RagIndexService(new FakeEmbeddingModel(), new InMemoryVectorStore(), 20, 5);

        List<DocumentChunk> chunks = indexService.splitDocument(61L,
                "0123456789abcdefghijKLMNOPQRSTuvwxyz");

        assertEquals(3, chunks.size());
        assertEquals(61L, chunks.get(0).documentId());
        assertEquals(0, chunks.get(0).chunkIndex());
        assertEquals("0123456789abcdefghij", chunks.get(0).text());
        assertEquals("0", chunks.get(0).metadata().get("charStart"));
        assertEquals("20", chunks.get(0).metadata().get("charEnd"));
        assertFalse(chunks.get(0).metadata().get("contentHash").isBlank());
        assertFalse(chunks.get(0).metadata().get("chunkId").isBlank());
        assertEquals("default", chunks.get(0).metadata().get("documentVersion"));
        assertEquals("0", chunks.get(0).metadata().get("startOffset"));
        assertEquals("20", chunks.get(0).metadata().get("endOffset"));
        assertEquals("rag-chunk-v2", chunks.get(0).metadata().get("chunkVersion"));
        assertEquals(1, chunks.get(1).chunkIndex());
        assertEquals("15", chunks.get(1).metadata().get("charStart"));
    }

    @Test
    void shouldGenerateDeterministicFakeEmbedding() {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel(64);

        EmbeddingVector first = embeddingModel.embed("Redis cache token bucket rate limit");
        EmbeddingVector second = embeddingModel.embed("Redis cache token bucket rate limit");
        EmbeddingVector different = embeddingModel.embed("RocketMQ outbox parser worker");

        assertEquals(first, second);
        assertEquals(64, first.dimension());
        assertNotEquals(first, different);
    }

    @Test
    void shouldSearchTopKChunksByVectorSimilarity() {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel(128);
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        DocumentChunk redisChunk = chunk(61L, 0, "Redis cache stores session tokens and rate limit counters.");
        DocumentChunk mqChunk = chunk(61L, 1, "RocketMQ outbox dispatches parser tasks to consumers.");
        DocumentChunk otherDocumentChunk = chunk(62L, 0, "Redis content from another document should be filtered.");
        vectorStore.add(redisChunk, embeddingModel.embed(redisChunk.text()));
        vectorStore.add(mqChunk, embeddingModel.embed(mqChunk.text()));
        vectorStore.add(otherDocumentChunk, embeddingModel.embed(otherDocumentChunk.text()));

        List<VectorSearchResult> results = vectorStore.searchTopK(61L, embeddingModel.embed("How does Redis cache work?"), 1);

        assertEquals(1, results.size());
        assertEquals(redisChunk, results.get(0).chunk());
        assertTrue(results.get(0).score() > 0.0D);
    }

    @Test
    void shouldRetrieveTopKByQuestion() {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel(128);
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        RagIndexService indexService = new RagIndexService(embeddingModel, vectorStore, 80, 10);
        RagRetrievalService retrievalService = new RagRetrievalService(embeddingModel, vectorStore);
        indexService.indexDocument(61L, """
                Redis cache keeps hot session context and token bucket counters.
                RocketMQ outbox guarantees parser task dispatch and retry.
                MinIO stores uploaded document objects and chunk files.
                """);

        List<VectorSearchResult> results = retrievalService.retrieveForQuestion(61L, "Where is cache session context stored?", 2);

        assertEquals(2, results.size());
        assertTrue(results.get(0).chunk().text().contains("Redis"));
        assertTrue(results.get(0).score() >= results.get(1).score());
    }

    @Test
    void shouldIndexThroughVectorStoreAbstraction() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        RagIndexService indexService = new RagIndexService(
                new FakeEmbeddingModel(),
                vectorStore,
                new RagIndexManager(),
                RagEmbeddingProperties.PROVIDER_FAKE,
                "custom_test_store",
                80,
                10
        );

        RagIndexService.RagIndexResult result = indexService.indexDocument(61L, RagIndexKey.DEFAULT_VERSION,
                "Redis cache keeps hot session context and token bucket counters.");

        assertTrue(result.chunkCount() > 0);
        assertEquals(1, vectorStore.deleteDocumentCalls);
        assertEquals(result.chunkCount(), vectorStore.addCalls);
        assertEquals("custom_test_store", result.state().vectorStoreType());
    }

    @Test
    void shouldRetrieveThroughVectorStoreAbstraction() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        DocumentChunk chunk = chunk(61L, 0, "Redis cache keeps hot session context.");
        vectorStore.add(chunk, new FakeEmbeddingModel().embed(chunk.text()));
        RagRetrievalService retrievalService = new RagRetrievalService(new FakeEmbeddingModel(), vectorStore);

        List<VectorSearchResult> results = retrievalService.retrieveForQuestion(61L, "Redis cache", 1);

        assertEquals(1, vectorStore.searchCalls);
        assertEquals(1, results.size());
        assertEquals(chunk, results.get(0).chunk());
    }

    @Test
    void shouldBuildAnswerContextWithCitations() {
        RagAnswerContextBuilder builder = new RagAnswerContextBuilder();
        DocumentChunk chunk = new DocumentChunk(61L, 2, "Agent trace records tool execution steps.",
                Map.of("charStart", "120", "charEnd", "164", "source", "unit-test"));
        VectorSearchResult hit = new VectorSearchResult(chunk, 0.875D);

        RagAnswerContext context = builder.build(List.of(hit));

        assertTrue(context.contextText().contains("[1] documentId=61, chunkIndex=2"));
        assertTrue(context.contextText().contains("score=0.8750"));
        assertTrue(context.contextText().contains("Agent trace records tool execution steps."));
        assertEquals(1, context.citations().size());
        RagCitation citation = context.citations().get(0);
        assertEquals(61L, citation.documentId());
        assertEquals(2, citation.chunkIndex());
        assertEquals("120", citation.metadata().get("charStart"));
        assertEquals("164", citation.metadata().get("charEnd"));
    }

    private DocumentChunk chunk(Long documentId, int chunkIndex, String text) {
        return new DocumentChunk(documentId, chunkIndex, text, Map.of("source", "test"));
    }

    private static class RecordingVectorStore implements VectorStore {

        private final java.util.ArrayList<VectorSearchResult> indexedResults = new java.util.ArrayList<>();
        private int addCalls;
        private int searchCalls;
        private int deleteDocumentCalls;

        @Override
        public void add(RagSearchScope scope, DocumentChunk chunk, EmbeddingVector vector) {
            addCalls++;
            indexedResults.add(new VectorSearchResult(chunk, 0.99D));
        }

        @Override
        public List<VectorSearchResult> searchTopK(RagSearchScope scope, EmbeddingVector queryVector, int topK) {
            searchCalls++;
            return indexedResults.stream()
                    .filter(result -> scope.documentId().equals(result.chunk().documentId()))
                    .limit(Math.max(0, topK))
                    .toList();
        }

        @Override
        public void deleteDocument(Long documentId) {
            deleteDocumentCalls++;
            indexedResults.removeIf(result -> documentId.equals(result.chunk().documentId()));
        }

        @Override
        public void clear() {
            indexedResults.clear();
        }
    }
}
