package com.docpilot.backend.ai.rag;

public record OpenAiCompatibleEmbeddingRequest(
        String model,
        String input
) {
}
