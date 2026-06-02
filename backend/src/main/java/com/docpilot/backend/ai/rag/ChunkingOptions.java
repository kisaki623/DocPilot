package com.docpilot.backend.ai.rag;

public record ChunkingOptions(int chunkSize, int overlap) {

    public static final int DEFAULT_CHUNK_SIZE = 600;
    public static final int DEFAULT_OVERLAP = 100;

    public ChunkingOptions {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be non-negative and smaller than chunkSize");
        }
    }

    public static ChunkingOptions defaults() {
        return new ChunkingOptions(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }
}
