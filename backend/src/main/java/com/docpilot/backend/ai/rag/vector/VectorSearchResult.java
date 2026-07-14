package com.docpilot.backend.ai.rag.vector;

import java.util.List;

public record VectorSearchResult(
        List<VectorSearchHit> hits,
        String provider,
        String collection
) {

    public VectorSearchResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
        provider = provider == null ? "" : provider.trim();
        collection = collection == null ? "" : collection.trim();
    }
}
