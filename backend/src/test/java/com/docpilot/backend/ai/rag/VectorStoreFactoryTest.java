package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorStoreFactoryTest {

    private final VectorStoreFactory factory = new VectorStoreFactory();

    @Test
    void shouldSelectInMemoryVectorStoreByDefault() {
        InMemoryVectorStore inMemoryVectorStore = new InMemoryVectorStore();

        VectorStore vectorStore = factory.create(new RagVectorStoreProperties(), inMemoryVectorStore);

        assertThat(vectorStore).isSameAs(inMemoryVectorStore);
    }

    @Test
    void shouldSelectDisabledQdrantSkeletonWithoutNetworkConfiguration() {
        RagVectorStoreProperties properties = new RagVectorStoreProperties();
        properties.setProvider("qdrant_disabled");
        properties.getQdrant().setEndpoint("");
        properties.getQdrant().setApiKey("");

        VectorStore vectorStore = factory.create(properties, new InMemoryVectorStore());

        assertThat(vectorStore).isInstanceOf(DisabledQdrantVectorStore.class);
        assertThat(((DisabledQdrantVectorStore) vectorStore).collection()).isEqualTo("docpilot_rag_demo");
    }

    @Test
    void shouldFailClearlyWhenDisabledQdrantSkeletonIsUsed() {
        RagVectorStoreProperties properties = new RagVectorStoreProperties();
        properties.setProvider("qdrant_disabled");
        VectorStore vectorStore = factory.create(properties, new InMemoryVectorStore());

        assertThatThrownBy(() -> vectorStore.searchTopK(61L, new FakeEmbeddingModel().embed("query"), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Qdrant vector store is disabled; skeleton does not perform HTTP requests.");
    }
}
