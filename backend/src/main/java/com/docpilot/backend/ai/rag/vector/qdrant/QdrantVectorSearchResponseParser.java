package com.docpilot.backend.ai.rag.vector.qdrant;

import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

class QdrantVectorSearchResponseParser {

    private final ObjectMapper objectMapper;

    QdrantVectorSearchResponseParser() {
        this(new ObjectMapper());
    }

    QdrantVectorSearchResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    List<VectorSearchHit> parseHits(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            throw new IllegalArgumentException("responseJson must not be blank");
        }
        Map<String, Object> root = readMap(responseJson);
        Object result = root.get("result");
        if (result instanceof List<?> list) {
            return parseHitList(list);
        }
        if (result instanceof Map<?, ?> map && map.get("points") instanceof List<?> points) {
            return parseHitList(points);
        }
        return List.of();
    }

    private List<VectorSearchHit> parseHitList(List<?> points) {
        return points.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::parseHit)
                .toList();
    }

    private VectorSearchHit parseHit(Map<?, ?> point) {
        Map<String, Object> payload = mapValue(point.get("payload"));
        return new VectorSearchHit(
                stringValue(point.get("id")),
                doubleValue(point.get("score")),
                longValue(payload.get("userId")),
                longValue(payload.get("documentId")),
                intValue(payload.get("indexVersion")),
                intValue(payload.get("chunkIndex")),
                content(payload),
                stringValue(payload.get("contentHash")),
                payload
        );
    }

    private String content(Map<String, Object> payload) {
        String content = stringValue(payload.get("content"));
        if (!content.isBlank()) {
            return content;
        }
        return stringValue(payload.get("text"));
    }

    private Map<String, Object> readMap(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to parse Qdrant search response.", ex);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return 0.0D;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
