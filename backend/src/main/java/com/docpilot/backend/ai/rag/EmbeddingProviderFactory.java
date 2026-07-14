package com.docpilot.backend.ai.rag;

import org.springframework.stereotype.Component;

@Component
public class EmbeddingProviderFactory {

    public EmbeddingProvider create(EmbeddingProperties properties) {
        EmbeddingProperties resolvedProperties = properties == null ? new EmbeddingProperties() : properties;
        if (resolvedProperties.isDisabledProvider()) {
            return new DisabledEmbeddingProvider();
        }
        if (resolvedProperties.isOpenAiCompatibleProvider()) {
            return new OpenAICompatibleEmbeddingProvider(resolvedProperties);
        }
        return new MockEmbeddingProvider(resolvedProperties.getDimension(), resolvedProperties.getModel());
    }

    public EmbeddingProvider create(RagEmbeddingProperties properties) {
        RagEmbeddingProperties resolvedProperties = properties == null ? new RagEmbeddingProperties() : properties;
        return create(resolvedProperties.toEmbeddingProperties());
    }
}
