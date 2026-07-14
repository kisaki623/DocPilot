package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingModelFactoryTest {

    private final EmbeddingModelFactory factory = new EmbeddingModelFactory();

    @Test
    void shouldCreateFakeEmbeddingModelByDefault() {
        EmbeddingModel model = factory.create(new RagEmbeddingProperties());

        assertThat(model.embed("hello").dimension()).isEqualTo(32);
    }

    @Test
    void shouldCreateFakeEmbeddingModelWithConfiguredDimension() {
        RagEmbeddingProperties properties = new RagEmbeddingProperties();
        properties.setProvider("fake");
        properties.setDimension(16);

        EmbeddingModel model = factory.create(properties);

        assertThat(model.embed("hello").dimension()).isEqualTo(16);
    }

    @Test
    void shouldCreateDisabledEmbeddingModel() {
        RagEmbeddingProperties properties = new RagEmbeddingProperties();
        properties.setProvider("disabled");

        EmbeddingModel model = factory.create(properties);

        assertThat(model).isInstanceOf(DisabledEmbeddingModel.class);
    }

    @Test
    void shouldCreateOpenAiCompatibleEmbeddingModel() {
        RagEmbeddingProperties properties = new RagEmbeddingProperties();
        properties.setProvider("openai_compatible");
        properties.setModel("embedding-model");
        properties.setBaseUrl("https://example.invalid/v1");
        properties.setApiKey("test-key-not-used");
        properties.setConnectTimeoutMs(1234);
        properties.setRequestTimeoutMs(5678);

        EmbeddingModel model = factory.create(properties);

        assertThat(model).isNotNull();
    }
}
