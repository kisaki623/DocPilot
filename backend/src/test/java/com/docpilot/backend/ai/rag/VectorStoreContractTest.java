package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorStoreContractTest {

    @Test
    void shouldUseInMemoryProviderByDefault() {
        RagVectorStoreProperties properties = new RagVectorStoreProperties();
        VectorStoreFactory factory = new VectorStoreFactory();
        InMemoryVectorStore inMemoryVectorStore = new InMemoryVectorStore();

        VectorStore vectorStore = factory.create(properties, inMemoryVectorStore);

        assertThat(properties.getProvider()).isEqualTo(RagVectorStoreProperties.PROVIDER_IN_MEMORY);
        assertThat(vectorStore).isSameAs(inMemoryVectorStore);
    }

    @Test
    void inMemoryShouldAddAndSearchTopK() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        DocumentChunk first = chunk(61L, 0);
        DocumentChunk second = chunk(61L, 1);
        vectorStore.add(first, vector(1.0D, 0.0D));
        vectorStore.add(second, vector(0.0D, 1.0D));

        List<VectorSearchResult> results = vectorStore.searchTopK(61L, vector(1.0D, 0.0D), 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunk()).isEqualTo(first);
        assertThat(results.get(0).score()).isEqualTo(1.0D);
    }

    @Test
    void inMemoryShouldIsolateDifferentDocuments() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        vectorStore.add(chunk(61L, 0), vector(1.0D, 0.0D));
        vectorStore.add(chunk(62L, 0), vector(1.0D, 0.0D));

        List<VectorSearchResult> results = vectorStore.searchTopK(61L, vector(1.0D, 0.0D), 10);

        assertThat(results)
                .hasSize(1)
                .allSatisfy(result -> assertThat(result.chunk().documentId()).isEqualTo(61L));
    }

    @Test
    void inMemoryShouldIsolateDifferentUsers() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        vectorStore.add(RagSearchScope.of("user-a", 61L), chunk(61L, 0), vector(1.0D, 0.0D));
        vectorStore.add(RagSearchScope.of("user-b", 61L), chunk(61L, 1), vector(1.0D, 0.0D));

        List<VectorSearchResult> results = vectorStore.searchTopK(RagSearchScope.of("user-a", 61L),
                vector(1.0D, 0.0D), 10);

        assertThat(results)
                .hasSize(1)
                .allSatisfy(result -> assertThat(result.chunk().chunkIndex()).isZero());
    }

    @Test
    void inMemoryShouldFailFastWhenSearchScopeMissing() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();

        assertThatThrownBy(() -> vectorStore.searchTopK((RagSearchScope) null, vector(1.0D, 0.0D), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope");
        assertThatThrownBy(() -> RagSearchScope.of(" ", 61L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        assertThatThrownBy(() -> RagSearchScope.of("user-a", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("documentId");
    }

    @Test
    void inMemoryShouldUseStableChunkIndexOrderingForTies() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        DocumentChunk later = chunk(61L, 2);
        DocumentChunk earlier = chunk(61L, 1);
        vectorStore.add(later, vector(1.0D, 0.0D));
        vectorStore.add(earlier, vector(1.0D, 0.0D));

        List<VectorSearchResult> results = vectorStore.searchTopK(61L, vector(1.0D, 0.0D), 2);

        assertThat(results).extracting(result -> result.chunk().chunkIndex())
                .containsExactly(1, 2);
    }

    @Test
    void qdrantDisabledShouldFailLocallyWithoutEndpointOrApiKey() {
        RagVectorStoreProperties properties = new RagVectorStoreProperties();
        properties.setProvider("qdrant_disabled");
        properties.getQdrant().setEndpoint("");
        properties.getQdrant().setApiKey("");
        VectorStore vectorStore = new VectorStoreFactory().create(properties, new InMemoryVectorStore());

        assertThat(vectorStore).isInstanceOf(DisabledQdrantVectorStore.class);
        assertThatThrownBy(() -> vectorStore.add(chunk(61L, 0), vector(1.0D, 0.0D)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled")
                .hasMessageContaining("does not perform HTTP requests");
        assertThatThrownBy(() -> vectorStore.searchTopK(61L, vector(1.0D, 0.0D), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled")
                .hasMessageContaining("does not perform HTTP requests");
        assertThatThrownBy(() -> vectorStore.clear())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled")
                .hasMessageContaining("does not perform HTTP requests");
    }

    @Test
    void propertiesShouldFailFastForUnknownProvider() {
        RagVectorStoreProperties properties = new RagVectorStoreProperties();

        assertThatThrownBy(() -> properties.setProvider("definitely_unknown_provider"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported app.rag.vector-store.provider");
    }

    private DocumentChunk chunk(Long documentId, int chunkIndex) {
        return new DocumentChunk(documentId, chunkIndex, "chunk-" + documentId + "-" + chunkIndex,
                Map.of("chunkHash", documentId + "-" + chunkIndex));
    }

    private EmbeddingVector vector(Double... values) {
        return new EmbeddingVector(List.of(values));
    }
}
