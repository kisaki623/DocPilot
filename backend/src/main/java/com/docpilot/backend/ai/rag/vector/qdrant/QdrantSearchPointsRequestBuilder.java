package com.docpilot.backend.ai.rag.vector.qdrant;

import com.docpilot.backend.ai.rag.vector.VectorSearchRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class QdrantSearchPointsRequestBuilder {

    private final ObjectMapper objectMapper;

    QdrantSearchPointsRequestBuilder() {
        this(new ObjectMapper());
    }

    QdrantSearchPointsRequestBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    String buildJson(VectorSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", request.queryVector().values());
        body.put("limit", request.topK());
        body.put("with_payload", true);
        body.put("filter", filter(request.userId(), request.documentId(), request.indexVersion()));
        return writeJson(body);
    }

    static Map<String, Object> filter(Long userId, Long documentId, Integer indexVersion) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (indexVersion != null && indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive when provided");
        }
        List<Map<String, Object>> must = new ArrayList<>();
        must.add(match("userId", userId));
        must.add(match("documentId", documentId));
        if (indexVersion != null) {
            must.add(match("indexVersion", indexVersion));
        }
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("must", must);
        return filter;
    }

    private static Map<String, Object> match(String key, Object value) {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("key", key);
        match.put("match", Map.of("value", value));
        return match;
    }

    private String writeJson(Map<String, Object> request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to build Qdrant search request.", ex);
        }
    }
}
