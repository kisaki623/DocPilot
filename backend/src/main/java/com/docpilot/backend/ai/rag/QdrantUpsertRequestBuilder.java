package com.docpilot.backend.ai.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QdrantUpsertRequestBuilder {

    private final ObjectMapper objectMapper;

    public QdrantUpsertRequestBuilder() {
        this(new ObjectMapper());
    }

    QdrantUpsertRequestBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public String buildJson(List<QdrantPointPayload> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("points", points.stream().map(this::point).toList());
        return writeJson(request);
    }

    private Map<String, Object> point(QdrantPointPayload pointPayload) {
        if (pointPayload == null) {
            throw new IllegalArgumentException("point must not be null");
        }
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", pointPayload.id());
        point.put("vector", pointPayload.vector().values());
        point.put("payload", pointPayload.payload());
        return point;
    }

    private String writeJson(Map<String, Object> request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to build Qdrant upsert request.", ex);
        }
    }
}
