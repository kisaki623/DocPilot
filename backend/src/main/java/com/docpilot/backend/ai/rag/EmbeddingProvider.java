package com.docpilot.backend.ai.rag;

import java.util.List;

public interface EmbeddingProvider {

    EmbeddingResult embed(EmbeddingRequest request);

    default List<EmbeddingResult> embedBatch(List<EmbeddingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .map(this::embed)
                .toList();
    }
}
