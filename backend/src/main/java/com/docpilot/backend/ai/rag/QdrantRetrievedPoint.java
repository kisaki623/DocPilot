package com.docpilot.backend.ai.rag;

import java.util.Map;

public record QdrantRetrievedPoint(
        String id,
        double score,
        Map<String, Object> payload
) {

    public QdrantRetrievedPoint {
        id = id == null ? "" : id.trim();
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public VectorSearchResult toVectorSearchResult() {
        Long documentId = longPayload("documentId");
        int chunkIndex = intPayload("chunkIndex");
        String text = stringPayload("text");
        Map<String, String> metadata = stringMapPayload("metadata");
        return new VectorSearchResult(new DocumentChunk(documentId, chunkIndex, text, metadata), score);
    }

    private Long longPayload(String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        throw new IllegalArgumentException("Missing numeric payload field: " + key);
    }

    private int intPayload(String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        throw new IllegalArgumentException("Missing numeric payload field: " + key);
    }

    private String stringPayload(String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing text payload field: " + key);
        }
        return value.toString();
    }

    private Map<String, String> stringMapPayload(String key) {
        Object value = payload.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().toString(),
                        entry -> entry.getValue().toString(),
                        (left, right) -> right
                ));
    }
}
