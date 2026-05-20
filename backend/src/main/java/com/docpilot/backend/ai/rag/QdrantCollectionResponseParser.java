package com.docpilot.backend.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class QdrantCollectionResponseParser {

    private final ObjectMapper objectMapper;

    public QdrantCollectionResponseParser() {
        this(new ObjectMapper());
    }

    QdrantCollectionResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public QdrantCollectionPreflightResult parseInfo(int statusCode, String responseBody) {
        if (statusCode == 404) {
            return QdrantCollectionPreflightResult.notFound();
        }
        if (statusCode < 200 || statusCode >= 300) {
            return QdrantCollectionPreflightResult.failed("qdrant_http_error");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody == null || responseBody.isBlank() ? "{}" : responseBody);
            String status = root.path("status").asText("");
            boolean exists = "ok".equalsIgnoreCase(status) || root.has("result");
            return exists
                    ? QdrantCollectionPreflightResult.collectionExists()
                    : QdrantCollectionPreflightResult.failed("qdrant_unexpected_response");
        } catch (Exception ex) {
            return QdrantCollectionPreflightResult.failed("qdrant_parse_error");
        }
    }
}
