package com.docpilot.backend.ai.rag;

public class DisabledEmbeddingModel implements EmbeddingModel {

    @Override
    public EmbeddingVector embed(String text) {
        throw new IllegalStateException("Embedding provider is disabled");
    }
}
