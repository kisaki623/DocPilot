package com.docpilot.backend.ai.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class QdrantCollectionCreateRequestBuilder {

    private final ObjectMapper objectMapper;

    public QdrantCollectionCreateRequestBuilder() {
        this(new ObjectMapper());
    }

    QdrantCollectionCreateRequestBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public String buildJson(int vectorSize, String distance) {
        if (vectorSize <= 0) {
            throw new IllegalArgumentException("vectorSize must be positive");
        }
        String normalizedDistance = normalizeDistance(distance);
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", vectorSize);
        vectors.put("distance", normalizedDistance);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("vectors", vectors);
        return writeJson(request);
    }

    private String normalizeDistance(String distance) {
        if (distance == null || distance.isBlank()) {
            return "Cosine";
        }
        String normalized = distance.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "cosine" -> "Cosine";
            case "dot", "dotproduct", "dot_product" -> "Dot";
            case "euclid", "euclidean" -> "Euclid";
            default -> throw new IllegalArgumentException("Unsupported Qdrant distance metric.");
        };
    }

    private String writeJson(Map<String, Object> request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to build Qdrant collection create request.", ex);
        }
    }
}
