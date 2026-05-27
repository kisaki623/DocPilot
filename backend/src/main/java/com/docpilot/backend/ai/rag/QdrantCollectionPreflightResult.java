package com.docpilot.backend.ai.rag;

public record QdrantCollectionPreflightResult(
        boolean exists,
        boolean createAllowed,
        boolean createAttempted,
        String status,
        String reason
) {

    public QdrantCollectionPreflightResult {
        status = status == null ? "" : status.trim();
        reason = reason == null ? "" : reason.trim();
    }

    public static QdrantCollectionPreflightResult collectionExists() {
        return new QdrantCollectionPreflightResult(true, false, false, "OK", "");
    }

    public static QdrantCollectionPreflightResult notFound() {
        return new QdrantCollectionPreflightResult(false, false, false, "BLOCKED", "qdrant_collection_missing");
    }

    public static QdrantCollectionPreflightResult failed(String reason) {
        return new QdrantCollectionPreflightResult(false, false, false, "FAILED", reason);
    }

    public QdrantCollectionPreflightResult withCreateAttempt(boolean allowed, boolean attempted, String status) {
        return new QdrantCollectionPreflightResult(exists, allowed, attempted, status, reason);
    }
}
