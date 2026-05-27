package com.docpilot.backend.ai.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QdrantSearchRequestBuilder {

    private final ObjectMapper objectMapper;

    public QdrantSearchRequestBuilder() {
        this(new ObjectMapper());
    }

    QdrantSearchRequestBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public String buildJson(String userId, Long documentId, EmbeddingVector queryVector, int topK) {
        return buildJson(RagSearchScope.of(userId, documentId), queryVector, topK);
    }

    public String buildJson(RagSearchScope scope, EmbeddingVector queryVector, int topK) {
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        if (queryVector == null) {
            throw new IllegalArgumentException("queryVector must not be null");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("vector", queryVector.values());
        request.put("limit", topK);
        request.put("with_payload", true);
        request.put("filter", filter(scope));
        return writeJson(request);
    }

    private Map<String, Object> filter(RagSearchScope scope) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("must", List.of(
                match("userId", scope.userId()),
                match("documentId", scope.documentId())
        ));
        return filter;
    }

    private Map<String, Object> match(String key, Object value) {
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
