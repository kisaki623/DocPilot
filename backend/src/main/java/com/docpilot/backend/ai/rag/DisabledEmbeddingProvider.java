package com.docpilot.backend.ai.rag;

import java.util.List;

public class DisabledEmbeddingProvider implements EmbeddingProvider {

    private static final String DISABLED_MESSAGE = "Embedding provider is disabled";

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        throw new IllegalStateException(DISABLED_MESSAGE);
    }

    @Override
    public List<EmbeddingResult> embedBatch(List<EmbeddingRequest> requests) {
        throw new IllegalStateException(DISABLED_MESSAGE);
    }
}
