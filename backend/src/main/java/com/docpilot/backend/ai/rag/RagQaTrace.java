package com.docpilot.backend.ai.rag;

public record RagQaTrace(
        boolean ragEnabled,
        String embeddingProvider,
        String vectorStoreType,
        boolean documentIdPresent,
        int topK,
        int retrievedCount,
        int maxContextChars,
        int contextChars,
        boolean contextTruncated,
        boolean contextHashPresent,
        boolean fallbackUsed,
        String fallbackReason,
        int citationCount,
        boolean cacheKeyRagAware,
        boolean indexReused,
        boolean indexTruncated
) {

    private static final String VECTOR_STORE_IN_MEMORY = "in_memory";
    private static final int MAX_FALLBACK_REASON_LENGTH = 80;

    public RagQaTrace {
        embeddingProvider = safeText(embeddingProvider);
        vectorStoreType = safeText(vectorStoreType);
        fallbackReason = safeFallbackReason(fallbackReason);
        topK = Math.max(0, topK);
        retrievedCount = Math.max(0, retrievedCount);
        maxContextChars = Math.max(0, maxContextChars);
        contextChars = Math.max(0, contextChars);
        citationCount = Math.max(0, citationCount);
    }

    public static RagQaTrace empty() {
        return new RagQaTrace(
                false,
                "",
                "",
                false,
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                "",
                0,
                false,
                false,
                false
        );
    }

    public static RagQaTrace disabled(String embeddingProvider) {
        return new RagQaTrace(
                false,
                embeddingProvider,
                VECTOR_STORE_IN_MEMORY,
                false,
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                "",
                0,
                false,
                false,
                false
        );
    }

    public static RagQaTrace retrieval(String embeddingProvider,
                                       boolean documentIdPresent,
                                       int topK,
                                       int retrievedCount,
                                       int maxContextChars,
                                       int contextChars,
                                       boolean contextTruncated,
                                       boolean contextHashPresent,
                                       int citationCount) {
        return retrieval(
                embeddingProvider,
                documentIdPresent,
                topK,
                retrievedCount,
                maxContextChars,
                contextChars,
                contextTruncated,
                contextHashPresent,
                citationCount,
                false
        );
    }

    public static RagQaTrace retrieval(String embeddingProvider,
                                       boolean documentIdPresent,
                                       int topK,
                                       int retrievedCount,
                                       int maxContextChars,
                                       int contextChars,
                                       boolean contextTruncated,
                                       boolean contextHashPresent,
                                       int citationCount,
                                       boolean indexReused) {
        return retrieval(
                embeddingProvider,
                VECTOR_STORE_IN_MEMORY,
                documentIdPresent,
                topK,
                retrievedCount,
                maxContextChars,
                contextChars,
                contextTruncated,
                contextHashPresent,
                citationCount,
                indexReused
        );
    }

    public static RagQaTrace retrieval(String embeddingProvider,
                                       String vectorStoreType,
                                       boolean documentIdPresent,
                                       int topK,
                                       int retrievedCount,
                                       int maxContextChars,
                                       int contextChars,
                                       boolean contextTruncated,
                                       boolean contextHashPresent,
                                       int citationCount,
                                       boolean indexReused) {
        return new RagQaTrace(
                true,
                embeddingProvider,
                vectorStoreType,
                documentIdPresent,
                topK,
                retrievedCount,
                maxContextChars,
                contextChars,
                contextTruncated,
                contextHashPresent,
                false,
                "",
                citationCount,
                false,
                indexReused,
                false
        );
    }

    public static RagQaTrace retrieval(String embeddingProvider,
                                       String vectorStoreType,
                                       boolean documentIdPresent,
                                       int topK,
                                       int retrievedCount,
                                       int maxContextChars,
                                       int contextChars,
                                       boolean contextTruncated,
                                       boolean contextHashPresent,
                                       int citationCount,
                                       boolean indexReused,
                                       boolean indexTruncated) {
        return new RagQaTrace(
                true,
                embeddingProvider,
                vectorStoreType,
                documentIdPresent,
                topK,
                retrievedCount,
                maxContextChars,
                contextChars,
                contextTruncated,
                contextHashPresent,
                false,
                "",
                citationCount,
                false,
                indexReused,
                indexTruncated
        );
    }

    public static RagQaTrace fallback(String embeddingProvider,
                                      boolean documentIdPresent,
                                      int topK,
                                      int maxContextChars,
                                      String fallbackReason) {
        return fallback(
                embeddingProvider,
                VECTOR_STORE_IN_MEMORY,
                documentIdPresent,
                topK,
                maxContextChars,
                fallbackReason
        );
    }

    public static RagQaTrace fallback(String embeddingProvider,
                                      String vectorStoreType,
                                      boolean documentIdPresent,
                                      int topK,
                                      int maxContextChars,
                                      String fallbackReason) {
        return new RagQaTrace(
                true,
                embeddingProvider,
                vectorStoreType,
                documentIdPresent,
                topK,
                0,
                maxContextChars,
                0,
                false,
                false,
                true,
                fallbackReason,
                0,
                false,
                false,
                false
        );
    }

    public RagQaTrace withCacheKeyRagAware(boolean cacheKeyRagAware) {
        return new RagQaTrace(
                ragEnabled,
                embeddingProvider,
                vectorStoreType,
                documentIdPresent,
                topK,
                retrievedCount,
                maxContextChars,
                contextChars,
                contextTruncated,
                contextHashPresent,
                fallbackUsed,
                fallbackReason,
                citationCount,
                cacheKeyRagAware,
                indexReused,
                indexTruncated
        );
    }

    private static String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private static String safeFallbackReason(String reason) {
        String normalized = safeText(reason).replaceAll("[\\r\\n\\t]+", " ");
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("authorization")
                || lower.contains("bearer")
                || lower.contains("api key")
                || lower.contains("apikey")
                || lower.contains("baseurl")
                || lower.contains("provider response")
                || lower.contains("prompt")
                || lower.contains("documenttext")) {
            return "redacted_fallback_reason";
        }
        if (normalized.length() <= MAX_FALLBACK_REASON_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_FALLBACK_REASON_LENGTH);
    }
}
