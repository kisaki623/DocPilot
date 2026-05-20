package com.docpilot.backend.ai.rag;

import java.util.LinkedHashMap;
import java.util.Map;

public record RagChunkMetadata(
        Long documentId,
        String documentVersion,
        int chunkIndex,
        String chunkId,
        String contentHash,
        int startOffset,
        int endOffset,
        boolean indexTruncated
) {

    private static final String CHUNK_VERSION = "rag-chunk-v2";

    public RagChunkMetadata {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        documentVersion = documentVersion == null || documentVersion.isBlank()
                ? RagIndexKey.DEFAULT_VERSION
                : documentVersion.trim();
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be non-negative");
        }
        if (chunkId == null || chunkId.isBlank()) {
            throw new IllegalArgumentException("chunkId must not be blank");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("chunk offsets are invalid");
        }
    }

    public Map<String, String> toMap() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("documentId", String.valueOf(documentId));
        metadata.put("documentVersion", documentVersion);
        metadata.put("chunkIndex", String.valueOf(chunkIndex));
        metadata.put("chunkId", chunkId);
        metadata.put("contentHash", contentHash);
        metadata.put("chunkHash", contentHash);
        metadata.put("startOffset", String.valueOf(startOffset));
        metadata.put("endOffset", String.valueOf(endOffset));
        metadata.put("charStart", String.valueOf(startOffset));
        metadata.put("charEnd", String.valueOf(endOffset));
        metadata.put("chunkVersion", CHUNK_VERSION);
        metadata.put("indexTruncated", String.valueOf(indexTruncated));
        return metadata;
    }
}
