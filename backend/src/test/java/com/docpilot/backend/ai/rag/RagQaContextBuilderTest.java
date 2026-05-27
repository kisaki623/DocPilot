package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagQaContextBuilderTest {

    @Test
    void shouldBuildLimitedRagQaContext() {
        RagQaContextBuilder builder = new RagQaContextBuilder();

        RagQaContext context = builder.build(
                61L,
                "Where is Redis cache used?",
                """
                        Redis cache keeps hot session context and token bucket counters.
                        RocketMQ outbox dispatches parser tasks to consumers.
                        MinIO stores uploaded objects.
                        """,
                2,
                120
        );

        assertThat(context.used()).isTrue();
        assertThat(context.chunkCount()).isGreaterThanOrEqualTo(1);
        assertThat(context.retrievedCount()).isLessThanOrEqualTo(2);
        assertThat(context.contextText()).hasSizeLessThanOrEqualTo(120);
        assertThat(context.contextText()).contains("documentId=61");
        assertThat(context.citations()).isNotEmpty();
    }

    @Test
    void shouldReturnEmptyWhenDocumentTextMissing() {
        RagQaContextBuilder builder = new RagQaContextBuilder();

        RagQaContext context = builder.build(61L, "question", "   ", 3, 200);

        assertThat(context.used()).isFalse();
        assertThat(context.contextText()).isEmpty();
        assertThat(context.citations()).isEmpty();
        assertThat(context.chunkCount()).isZero();
        assertThat(context.retrievedCount()).isZero();
    }

    @Test
    void shouldRespectMaxContextChars() {
        RagQaContextBuilder builder = new RagQaContextBuilder();

        RagQaContext context = builder.build(
                61L,
                "cache",
                "Redis cache ".repeat(200),
                3,
                50
        );

        assertThat(context.used()).isTrue();
        assertThat(context.contextText()).hasSizeLessThanOrEqualTo(50);
    }

    @Test
    void shouldBuildContextThroughInjectedVectorStoreAbstraction() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        RagVectorStoreProperties vectorStoreProperties = new RagVectorStoreProperties();
        vectorStoreProperties.setProvider("in_memory");
        RagQaContextBuilder builder = new RagQaContextBuilder(
                new EmbeddingModelFactory(),
                new RagEmbeddingProperties(),
                vectorStore,
                new RagIndexManager(),
                vectorStoreProperties,
                new VectorStoreFactory()
        );

        RagQaContext context = builder.build(
                61L,
                "Where is Redis cache used?",
                "Redis cache keeps hot session context and token bucket counters.",
                2,
                300
        );

        assertThat(vectorStore.deleteDocumentCalls).isEqualTo(1);
        assertThat(vectorStore.addCalls).isGreaterThan(0);
        assertThat(vectorStore.searchCalls).isEqualTo(1);
        assertThat(vectorStore.lastSearchScope).isEqualTo(RagSearchScope.system(61L));
        assertThat(context.used()).isTrue();
        assertThat(context.trace().vectorStoreType()).isEqualTo("in_memory");
        assertThat(context.retrievedCount()).isGreaterThan(0);
    }

    private static class RecordingVectorStore implements VectorStore {

        private final List<VectorSearchResult> indexedResults = new ArrayList<>();
        private int addCalls;
        private int searchCalls;
        private int deleteDocumentCalls;
        private RagSearchScope lastSearchScope;

        @Override
        public void add(RagSearchScope scope, DocumentChunk chunk, EmbeddingVector vector) {
            addCalls++;
            indexedResults.add(new VectorSearchResult(chunk, 0.99D));
        }

        @Override
        public List<VectorSearchResult> searchTopK(RagSearchScope scope, EmbeddingVector queryVector, int topK) {
            searchCalls++;
            lastSearchScope = scope;
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
