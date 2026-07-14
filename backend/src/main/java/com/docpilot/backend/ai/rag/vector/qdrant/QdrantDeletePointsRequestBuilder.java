package com.docpilot.backend.ai.rag.vector.qdrant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

class QdrantDeletePointsRequestBuilder {

    private final ObjectMapper objectMapper;

    QdrantDeletePointsRequestBuilder() {
        this(new ObjectMapper());
    }

    QdrantDeletePointsRequestBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    String buildJson(Long userId, Long documentId, Integer indexVersion) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("filter", QdrantSearchPointsRequestBuilder.filter(userId, documentId, indexVersion));
        return writeJson(request);
    }

    private String writeJson(Map<String, Object> request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to build Qdrant delete request.", ex);
        }
    }
}
