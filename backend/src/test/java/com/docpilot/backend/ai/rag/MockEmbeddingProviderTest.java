package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockEmbeddingProviderTest {

    @Test
    void shouldGenerateDeterministicVectorForSameInputAndDimension() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(16);

        EmbeddingVector first = provider.embed(EmbeddingRequest.of("Redis cache evidence")).vector();
        EmbeddingVector second = provider.embed(EmbeddingRequest.of("Redis cache evidence")).vector();

        assertThat(first.values()).isEqualTo(second.values());
        assertThat(first.dimension()).isEqualTo(16);
    }

    @Test
    void shouldGenerateDifferentVectorForDifferentInput() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(16);

        EmbeddingVector first = provider.embed(EmbeddingRequest.of("Redis cache evidence")).vector();
        EmbeddingVector second = provider.embed(EmbeddingRequest.of("RocketMQ outbox evidence")).vector();

        assertThat(first.values()).isNotEqualTo(second.values());
    }

    @Test
    void shouldReturnZeroVectorForBlankInput() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(8);

        EmbeddingResult result = provider.embed(EmbeddingRequest.of("   "));

        assertThat(result.vector().dimension()).isEqualTo(8);
        assertThat(result.vector().values()).containsOnly(0.0d);
    }

    @Test
    void shouldKeepBatchOrderAndMetadata() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(8, "mock-model");
        List<EmbeddingRequest> requests = List.of(
                new EmbeddingRequest("first", "", Map.of("chunkIndex", "0")),
                new EmbeddingRequest("second", "", Map.of("chunkIndex", "1")),
                new EmbeddingRequest("third", "", Map.of("chunkIndex", "2"))
        );

        List<EmbeddingResult> results = provider.embedBatch(requests);

        assertThat(results).hasSize(3);
        assertThat(results).extracting(EmbeddingResult::model).containsExactly("mock-model", "mock-model", "mock-model");
        assertThat(results).extracting(result -> result.metadata().get("chunkIndex"))
                .containsExactly("0", "1", "2");
        assertThat(results.get(0).vector().values())
                .isEqualTo(provider.embed(EmbeddingRequest.of("first")).vector().values());
        assertThat(results.get(1).vector().values())
                .isEqualTo(provider.embed(EmbeddingRequest.of("second")).vector().values());
    }
}
