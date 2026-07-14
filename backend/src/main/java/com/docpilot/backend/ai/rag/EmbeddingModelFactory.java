package com.docpilot.backend.ai.rag;

import org.springframework.stereotype.Component;

@Component
public class EmbeddingModelFactory {

    private final EmbeddingProviderFactory embeddingProviderFactory;

    public EmbeddingModelFactory() {
        this(new EmbeddingProviderFactory());
    }

    public EmbeddingModelFactory(EmbeddingProviderFactory embeddingProviderFactory) {
        this.embeddingProviderFactory = embeddingProviderFactory == null ? new EmbeddingProviderFactory() : embeddingProviderFactory;
    }

    public EmbeddingModel create(RagEmbeddingProperties properties) {
        RagEmbeddingProperties resolvedProperties = properties == null ? new RagEmbeddingProperties() : properties;
        if (resolvedProperties.isDisabledProvider()) {
            return new DisabledEmbeddingModel();
        }
        return new ProviderBackedEmbeddingModel(embeddingProviderFactory.create(resolvedProperties));
    }

    private record ProviderBackedEmbeddingModel(EmbeddingProvider provider) implements EmbeddingModel {

        @Override
        public EmbeddingVector embed(String text) {
            return provider.embed(EmbeddingRequest.of(text)).vector();
        }
    }
}
