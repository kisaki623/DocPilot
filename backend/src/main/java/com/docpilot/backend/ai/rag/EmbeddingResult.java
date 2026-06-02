package com.docpilot.backend.ai.rag;

import java.util.Map;

public record EmbeddingResult(
        EmbeddingVector vector,
        String provider,
        String model,
        int dimension,
        Map<String, String> metadata
) {

    public EmbeddingResult {
        if (vector == null) {
            throw new IllegalArgumentException("vector must not be null");
        }
        provider = provider == null ? "" : provider.trim();
        model = model == null ? "" : model.trim();
        dimension = dimension <= 0 ? vector.dimension() : dimension;
        if (dimension != vector.dimension()) {
            throw new IllegalArgumentException("dimension must match vector dimension");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
