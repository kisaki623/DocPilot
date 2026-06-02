package com.docpilot.backend.ai.rag.vector;

import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import com.docpilot.backend.ai.rag.EmbeddingVector;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record VectorPoint(
        String id,
        Long userId,
        Long documentId,
        Integer indexVersion,
        Integer chunkIndex,
        String content,
        String contentHash,
        EmbeddingVector vector,
        Map<String, Object> metadata
) {

    public VectorPoint {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (indexVersion == null || indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive");
        }
        if (chunkIndex == null || chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (vector == null) {
            throw new IllegalArgumentException("vector must not be null");
        }
        id = id.trim();
        content = content.trim();
        contentHash = contentHash.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static VectorPoint fromDocumentChunk(DocumentChunkEntity chunk, EmbeddingVector vector) {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk must not be null");
        }
        String id = safeText(chunk.getVectorId());
        if (id.isBlank()) {
            id = stableId(chunk.getUserId(), chunk.getDocumentId(), chunk.getIndexVersion(),
                    chunk.getChunkIndex(), chunk.getContentHash());
        }
        return new VectorPoint(
                id,
                chunk.getUserId(),
                chunk.getDocumentId(),
                chunk.getIndexVersion(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getContentHash(),
                vector,
                metadata(chunk)
        );
    }

    public Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(metadata);
        payload.put("userId", userId);
        payload.put("documentId", documentId);
        payload.put("indexVersion", indexVersion);
        payload.put("chunkIndex", chunkIndex);
        payload.put("content", content);
        payload.put("contentHash", contentHash);
        return payload;
    }

    static String stableId(Long userId,
                           Long documentId,
                           Integer indexVersion,
                           Integer chunkIndex,
                           String contentHash) {
        String raw = userId + ":" + documentId + ":" + indexVersion + ":" + chunkIndex + ":" + contentHash;
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static Map<String, Object> metadata(DocumentChunkEntity chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfNotNull(metadata, "startOffset", chunk.getStartOffset());
        putIfNotNull(metadata, "endOffset", chunk.getEndOffset());
        putIfNotNull(metadata, "tokenCount", chunk.getTokenCount());
        putIfNotBlank(metadata, "embeddingModel", chunk.getEmbeddingModel());
        return metadata;
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
