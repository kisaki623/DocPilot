package com.docpilot.backend.ai.rag.vector;

import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.vector.inmemory.InMemoryVectorStoreClient;
import com.docpilot.backend.ai.rag.vector.qdrant.QdrantVectorStoreClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreClientFactoryTest {

    private final VectorStoreClientFactory factory = new VectorStoreClientFactory();

    @Test
    void shouldCreateInMemoryClientByDefault() {
        VectorStoreClient client = factory.create(new RagVectorStoreProperties());

        assertThat(client).isInstanceOf(InMemoryVectorStoreClient.class);
    }

    @Test
    void shouldCreateDisabledClientWhenQdrantDisabledProvider() {
        RagVectorStoreProperties properties = new RagVectorStoreProperties();
        properties.setProvider("qdrant_disabled");

        VectorStoreClient client = factory.create(properties);

        assertThat(client).isInstanceOf(DisabledVectorStoreClient.class);
    }

    @Test
    void shouldCreateQdrantClientWhenQdrantProvider() {
        RagVectorStoreProperties properties = new RagVectorStoreProperties();
        properties.setProvider("qdrant");
        properties.getQdrant().setEndpoint("http://127.0.0.1:6333");

        VectorStoreClient client = factory.create(properties);

        assertThat(client).isInstanceOf(QdrantVectorStoreClient.class);
    }
}
