package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagIndexLifecycleTest {

    private static final long DOCUMENT_ID = 61L;
    private static final String VERSION_1 = "2026-05-21T00:00:00";
    private static final String VERSION_2 = "2026-05-21T00:10:00";

    private final InMemoryVectorStore vectorStore = new InMemoryVectorStore();
    private final RagIndexManager indexManager = new RagIndexManager();
    private final RagIndexService indexService = new RagIndexService(
            new FakeEmbeddingModel(),
            vectorStore,
            indexManager,
            RagEmbeddingProperties.PROVIDER_FAKE,
            RagIndexManager.VECTOR_STORE_IN_MEMORY,
            80,
            10
    );

    @Test
    void shouldWriteChunksOnFirstIndex() {
        RagIndexService.RagIndexResult result = indexService.indexDocument(DOCUMENT_ID, VERSION_1, documentText("Redis"));

        assertThat(result.chunkCount()).isGreaterThan(0);
        assertThat(result.chunks()).hasSize(result.chunkCount());
        assertThat(result.state().indexReused()).isFalse();
        assertThat(vectorStore.size()).isEqualTo(result.chunkCount());
        assertThat(indexManager.getState(DOCUMENT_ID, VERSION_1)).isPresent();
    }

    @Test
    void shouldSkipDuplicateDocumentVersionAndContentHash() {
        RagIndexService.RagIndexResult first = indexService.indexDocument(DOCUMENT_ID, VERSION_1, documentText("Redis"));

        RagIndexService.RagIndexResult second = indexService.indexDocument(DOCUMENT_ID, VERSION_1, documentText("Redis"));

        assertThat(second.chunks()).isEmpty();
        assertThat(second.chunkCount()).isEqualTo(first.chunkCount());
        assertThat(second.state().indexReused()).isTrue();
        assertThat(second.state().contentHash()).isEqualTo(first.state().contentHash());
        assertThat(vectorStore.size()).isEqualTo(first.chunkCount());
    }

    @Test
    void shouldReindexWhenContentHashChanges() {
        RagIndexService.RagIndexResult first = indexService.indexDocument(DOCUMENT_ID, VERSION_1, documentText("Redis"));

        RagIndexService.RagIndexResult second = indexService.indexDocument(DOCUMENT_ID, VERSION_1, documentText("RocketMQ"));

        assertThat(second.state().indexReused()).isFalse();
        assertThat(second.state().contentHash()).isNotEqualTo(first.state().contentHash());
        assertThat(vectorStore.size()).isEqualTo(second.chunkCount());
    }

    @Test
    void shouldReindexWhenDocumentVersionChanges() {
        RagIndexService.RagIndexResult first = indexService.indexDocument(DOCUMENT_ID, VERSION_1, documentText("Redis"));

        RagIndexService.RagIndexResult second = indexService.indexDocument(DOCUMENT_ID, VERSION_2, documentText("Redis"));

        assertThat(second.state().indexReused()).isFalse();
        assertThat(second.state().key().documentVersion()).isEqualTo(VERSION_2);
        assertThat(second.state().contentHash()).isEqualTo(first.state().contentHash());
        assertThat(indexManager.getState(DOCUMENT_ID, VERSION_1)).isEmpty();
        assertThat(indexManager.getState(DOCUMENT_ID, VERSION_2)).isPresent();
        assertThat(vectorStore.size()).isEqualTo(second.chunkCount());
    }

    @Test
    void shouldKeepDifferentDocumentsIsolated() {
        RagIndexService.RagIndexResult first = indexService.indexDocument(DOCUMENT_ID, VERSION_1, documentText("Redis"));
        RagIndexService.RagIndexResult second = indexService.indexDocument(62L, VERSION_1, documentText("MinIO"));

        assertThat(indexManager.getState(DOCUMENT_ID, VERSION_1)).isPresent();
        assertThat(indexManager.getState(62L, VERSION_1)).isPresent();
        assertThat(vectorStore.size()).isEqualTo(first.chunkCount() + second.chunkCount());
        assertThat(search(DOCUMENT_ID, "Redis")).isNotEmpty();
        assertThat(search(62L, "MinIO")).isNotEmpty();
    }

    @Test
    void shouldReindexAfterClear() {
        RagIndexService.RagIndexResult first = indexService.indexDocument(DOCUMENT_ID, VERSION_1, documentText("Redis"));
        indexManager.clear();

        RagIndexService.RagIndexResult second = indexService.indexDocument(DOCUMENT_ID, VERSION_1, documentText("Redis"));

        assertThat(second.state().indexReused()).isFalse();
        assertThat(second.chunks()).hasSize(first.chunkCount());
        assertThat(vectorStore.size()).isEqualTo(second.chunkCount());
    }

    @Test
    void shouldExposeIndexReusedInTrace() {
        RagQaContextBuilder builder = new RagQaContextBuilder(
                new EmbeddingModelFactory(),
                new RagEmbeddingProperties(),
                new InMemoryVectorStore(),
                new RagIndexManager()
        );
        String text = documentText("Redis");

        RagQaContext first = builder.build(DOCUMENT_ID, "Where is Redis used?", text, 2, 400);
        RagQaContext second = builder.build(DOCUMENT_ID, "Where is Redis used?", text, 2, 400);

        assertThat(first.trace().indexReused()).isFalse();
        assertThat(second.trace().indexReused()).isTrue();
        assertThat(new RagQaTraceFormatter().format(second.trace())).contains("indexReused=true");
    }

    private List<VectorSearchResult> search(Long documentId, String question) {
        RagRetrievalService retrievalService = new RagRetrievalService(new FakeEmbeddingModel(), vectorStore);
        return retrievalService.retrieveForQuestion(documentId, question, 1);
    }

    private String documentText(String keyword) {
        return keyword + " keeps demo evidence for RAG index lifecycle tracking.";
    }
}
