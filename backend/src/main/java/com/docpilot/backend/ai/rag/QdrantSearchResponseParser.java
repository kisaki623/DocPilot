package com.docpilot.backend.ai.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class QdrantSearchResponseParser {

    private final ObjectMapper objectMapper;

    public QdrantSearchResponseParser() {
        this(new ObjectMapper());
    }

    QdrantSearchResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public List<QdrantRetrievedPoint> parsePoints(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            throw new IllegalArgumentException("responseJson must not be blank");
        }
        Map<String, Object> root = readMap(responseJson);
        Object result = root.get("result");
        if (result instanceof List<?> list) {
            return parsePointList(list);
        }
        if (result instanceof Map<?, ?> map && map.get("points") instanceof List<?> points) {
            return parsePointList(points);
        }
        return List.of();
    }

    private List<QdrantRetrievedPoint> parsePointList(List<?> points) {
        return points.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::parsePoint)
                .toList();
    }

    private QdrantRetrievedPoint parsePoint(Map<?, ?> point) {
        String id = stringValue(point.get("id"));
        double score = doubleValue(point.get("score"));
        Map<String, Object> payload = mapValue(point.get("payload"));
        return new QdrantRetrievedPoint(id, score, payload);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
