package com.docpilot.backend.ai.rag;

import java.time.Instant;

public record RagIndexState(
        RagIndexKey key,
        Instant indexedAt,
        int chunkCount,
        String embeddingProvider,
        String vectorStoreType,
        String contentHash,
        boolean indexReused
) {

    public RagIndexState {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        indexedAt = indexedAt == null ? Instant.EPOCH : indexedAt;
        chunkCount = Math.max(0, chunkCount);
        embeddingProvider = safeText(embeddingProvider);
        vectorStoreType = safeText(vectorStoreType);
        contentHash = safeText(contentHash);
    }

    public RagIndexState asReused() {
        return new RagIndexState(
                key,
                indexedAt,
                chunkCount,
                embeddingProvider,
                vectorStoreType,
                contentHash,
                true
        );
    }

    private static String safeText(String text) {
        return text == null ? "" : text.trim();
    }
}
