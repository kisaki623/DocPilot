package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingProviderFactoryTest {

    private final EmbeddingProviderFactory factory = new EmbeddingProviderFactory();

    @Test
    void shouldCreateMockProviderByDefault() {
        EmbeddingProvider provider = factory.create(new EmbeddingProperties());

        assertThat(provider).isInstanceOf(MockEmbeddingProvider.class);
        assertThat(provider.embed(EmbeddingRequest.of("hello")).vector().dimension()).isEqualTo(32);
    }

    @Test
    void shouldCreateMockProviderFromLegacyRagProperties() {
        RagEmbeddingProperties properties = new RagEmbeddingProperties();
        properties.setProvider("fake");
        properties.setDimension(16);

        EmbeddingProvider provider = factory.create(properties);

        assertThat(provider).isInstanceOf(MockEmbeddingProvider.class);
        assertThat(provider.embed(EmbeddingRequest.of("hello")).vector().dimension()).isEqualTo(16);
    }

    @Test
    void shouldCreateDisabledProviderWhenDisabled() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(false);

        EmbeddingProvider provider = factory.create(properties);

        assertThat(provider).isInstanceOf(DisabledEmbeddingProvider.class);
    }

    @Test
    void shouldCreateOpenAiCompatibleProvider() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setProvider("openai_compatible");
        properties.setModel("embedding-model");
        properties.setBaseUrl("https://example.invalid/v1");
        properties.setApiKey("test-key-not-used");

        EmbeddingProvider provider = factory.create(properties);

        assertThat(provider).isInstanceOf(OpenAICompatibleEmbeddingProvider.class);
    }
}
