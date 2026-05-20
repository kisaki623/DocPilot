package com.docpilot.backend.ai.rag;

import org.springframework.stereotype.Component;

@Component
public class EmbeddingModelFactory {

    public EmbeddingModel create(RagEmbeddingProperties properties) {
        RagEmbeddingProperties resolvedProperties = properties == null ? new RagEmbeddingProperties() : properties;
        if (resolvedProperties.isDisabledProvider()) {
            return new DisabledEmbeddingModel();
        }
        if (resolvedProperties.isOpenAiCompatibleProvider()) {
            return new OpenAiCompatibleEmbeddingModel(
                    resolvedProperties.getModel(),
                    resolvedProperties.getBaseUrl(),
                    resolvedProperties.getApiKey(),
                    resolvedProperties.getConnectTimeoutMs(),
                    resolvedProperties.getRequestTimeoutMs()
            );
        }
        return new FakeEmbeddingModel(resolvedProperties.getDimension());
    }
}
