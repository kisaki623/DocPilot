package com.docpilot.backend.ai.rag;

import java.net.http.HttpTimeoutException;

public final class RagFallbackReasonClassifier {

    private RagFallbackReasonClassifier() {
    }

    public static String classify(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpTimeoutException) {
                return "qdrant_timeout";
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("qdrant vector store is disabled")) {
                    return "qdrant_disabled";
                }
                if (normalized.contains("qdrant") && normalized.contains("status")) {
                    return "qdrant_http_error";
                }
                if (normalized.contains("timed out") || normalized.contains("timeout")) {
                    return "qdrant_timeout";
                }
            }
            current = current.getCause();
        }
        return "rag_retrieval_failed";
    }
}
