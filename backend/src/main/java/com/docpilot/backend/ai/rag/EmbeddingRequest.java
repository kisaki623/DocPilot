package com.docpilot.backend.ai.rag;

import java.util.Map;

public record EmbeddingRequest(
        String input,
        String model,
        Map<String, String> metadata
) {

    public EmbeddingRequest {
        input = input == null ? "" : input;
        model = model == null ? "" : model.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static EmbeddingRequest of(String input) {
        return new EmbeddingRequest(input, "", Map.of());
    }

    public static EmbeddingRequest of(String input, String model) {
        return new EmbeddingRequest(input, model, Map.of());
    }
}
