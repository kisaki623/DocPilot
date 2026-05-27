package com.docpilot.backend.ai.rag;

import java.util.List;

public record EmbeddingVector(List<Double> values) {

    public EmbeddingVector {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        values = List.copyOf(values);
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("values must be finite");
            }
        }
    }

    public int dimension() {
        return values.size();
    }
}
