package com.docpilot.backend.ai.rag.vector.qdrant;

import com.docpilot.backend.ai.rag.vector.VectorPoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class QdrantUpsertPointsRequestBuilder {

    private final ObjectMapper objectMapper;

    QdrantUpsertPointsRequestBuilder() {
        this(new ObjectMapper());
    }

    QdrantUpsertPointsRequestBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    String buildJson(List<VectorPoint> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("points", points.stream().map(this::point).toList());
        return writeJson(request);
    }

    private Map<String, Object> point(VectorPoint vectorPoint) {
        if (vectorPoint == null) {
            throw new IllegalArgumentException("point must not be null");
        }
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", vectorPoint.id());
        point.put("vector", vectorPoint.vector().values());
        point.put("payload", vectorPoint.payload());
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
