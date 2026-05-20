package com.docpilot.backend.ai.rag;

import java.util.LinkedHashMap;
import java.util.Map;

public record QdrantPointPayload(
        String id,
        EmbeddingVector vector,
        Map<String, Object> payload
) {

    public static final String DEFAULT_USER_ID = "system";
    public static final String DEFAULT_DOCUMENT_VERSION = RagIndexKey.DEFAULT_VERSION;

    public QdrantPointPayload {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (vector == null) {
            throw new IllegalArgumentException("vector must not be null");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static QdrantPointPayload fromChunk(DocumentChunk chunk, EmbeddingVector vector) {
        return fromChunk(DEFAULT_USER_ID, DEFAULT_DOCUMENT_VERSION, chunk, vector);
    }

    public static QdrantPointPayload fromChunk(String userId,
                                               String documentVersion,
                                               DocumentChunk chunk,
                                               EmbeddingVector vector) {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk must not be null");
        }
        String normalizedUserId = safeText(userId, DEFAULT_USER_ID);
        String normalizedVersion = safeText(documentVersion, DEFAULT_DOCUMENT_VERSION);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", normalizedUserId);
        payload.put("documentId", chunk.documentId());
        payload.put("documentVersion", normalizedVersion);
        payload.put("chunkIndex", chunk.chunkIndex());
        payload.put("text", chunk.text());
        payload.put("contentHash", metadataValue(chunk, "contentHash"));
        payload.put("chunkHash", metadataValue(chunk, "chunkHash"));
        payload.put("citation", citationMetadata(chunk.metadata()));
        payload.put("metadata", safeMetadata(chunk.metadata()));
        return new QdrantPointPayload(pointId(chunk, normalizedVersion), vector, payload);
    }

    private static String pointId(DocumentChunk chunk, String documentVersion) {
        String hash = metadataValue(chunk, "contentHash");
        if (hash.isBlank()) {
            hash = metadataValue(chunk, "chunkHash");
        }
        if (hash.isBlank()) {
            hash = "chunk-" + chunk.chunkIndex();
        }
        return chunk.documentId() + ":" + documentVersion + ":" + chunk.chunkIndex() + ":" + hash;
    }

    private static Map<String, Object> citationMetadata(Map<String, String> metadata) {
        Map<String, Object> citation = new LinkedHashMap<>();
        copyIfPresent(metadata, citation, "charStart");
        copyIfPresent(metadata, citation, "charEnd");
        copyIfPresent(metadata, citation, "chunkVersion");
        copyIfPresent(metadata, citation, "source");
        return citation;
    }

    private static Map<String, Object> safeMetadata(Map<String, String> metadata) {
        Map<String, Object> safe = new LinkedHashMap<>();
        copyIfPresent(metadata, safe, "contentHash");
        copyIfPresent(metadata, safe, "chunkHash");
        copyIfPresent(metadata, safe, "charStart");
        copyIfPresent(metadata, safe, "charEnd");
        copyIfPresent(metadata, safe, "chunkVersion");
        copyIfPresent(metadata, safe, "source");
        return safe;
    }

    private static void copyIfPresent(Map<String, String> source, Map<String, Object> target, String key) {
        String value = source.get(key);
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String metadataValue(DocumentChunk chunk, String key) {
        String value = chunk.metadata().get(key);
        return value == null ? "" : value.trim();
    }

    private static String safeText(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        return text.trim();
    }
}
