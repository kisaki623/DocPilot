package com.docpilot.backend.ai.rag;

public class RagChunkingPolicy {

    public static final int DEFAULT_MAX_CHUNK_CHARS = 600;
    public static final int DEFAULT_OVERLAP_CHARS = 120;
    public static final int DEFAULT_MAX_CHUNKS_PER_DOCUMENT = 1000;

    private final int maxChunkChars;
    private final int overlapChars;
    private final int maxChunksPerDocument;

    public RagChunkingPolicy() {
        this(DEFAULT_MAX_CHUNK_CHARS, DEFAULT_OVERLAP_CHARS, DEFAULT_MAX_CHUNKS_PER_DOCUMENT);
    }

    public RagChunkingPolicy(int maxChunkChars, int overlapChars, int maxChunksPerDocument) {
        if (maxChunkChars <= 0) {
            throw new IllegalArgumentException("maxChunkChars must be positive");
        }
        if (overlapChars < 0 || overlapChars >= maxChunkChars) {
            throw new IllegalArgumentException("overlapChars must be non-negative and smaller than maxChunkChars");
        }
        if (maxChunksPerDocument <= 0) {
            throw new IllegalArgumentException("maxChunksPerDocument must be positive");
        }
        this.maxChunkChars = maxChunkChars;
        this.overlapChars = overlapChars;
        this.maxChunksPerDocument = maxChunksPerDocument;
    }

    public static RagChunkingPolicy defaults() {
        return new RagChunkingPolicy();
    }

    public static RagChunkingPolicy of(int maxChunkChars, int overlapChars) {
        return new RagChunkingPolicy(maxChunkChars, overlapChars, DEFAULT_MAX_CHUNKS_PER_DOCUMENT);
    }

    public int maxChunkChars() {
        return maxChunkChars;
    }

    public int overlapChars() {
        return overlapChars;
    }

    public int maxChunksPerDocument() {
        return maxChunksPerDocument;
    }
}
