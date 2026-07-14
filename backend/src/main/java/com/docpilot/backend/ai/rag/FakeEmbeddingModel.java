package com.docpilot.backend.ai.rag;

public class FakeEmbeddingModel implements EmbeddingModel {

    private static final int DEFAULT_DIMENSION = 32;

    private final MockEmbeddingProvider provider;

    public FakeEmbeddingModel() {
        this(DEFAULT_DIMENSION);
    }

    public FakeEmbeddingModel(int dimension) {
        this.provider = new MockEmbeddingProvider(dimension);
    }

    @Override
    public EmbeddingVector embed(String text) {
        return provider.embed(EmbeddingRequest.of(text)).vector();
    }
}
