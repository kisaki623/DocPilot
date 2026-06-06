package com.docpilot.backend.ai.rag;

public record ChunkingOptions(int chunkSize, int overlap) {

    public static final int DEFAULT_CHUNK_SIZE = 800;
    public static final int DEFAULT_OVERLAP = 120;

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
