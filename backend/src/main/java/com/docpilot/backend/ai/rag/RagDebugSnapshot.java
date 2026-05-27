package com.docpilot.backend.ai.rag;

public record RagDebugSnapshot(
        boolean ragEnabled,
        String embeddingProvider,
        String vectorStoreProvider,
        String vectorStoreType,
        boolean documentIdPresent,
        boolean userIdPresent,
        int topK,
        int retrievedCount,
        int chunkCount,
        boolean indexReused,
        boolean indexTruncated,
        int contextChars,
        boolean contextTruncated,
        boolean contextHashPresent,
        boolean fallbackUsed,
        String fallbackReason,
        int citationCount,
        boolean cacheKeyRagAware
) {

    private static final int MAX_REASON_LENGTH = 80;

    public RagDebugSnapshot {
        embeddingProvider = safeText(embeddingProvider);
        vectorStoreProvider = safeText(vectorStoreProvider);
        vectorStoreType = safeText(vectorStoreType);
        fallbackReason = safeReason(fallbackReason);
        topK = Math.max(0, topK);
        retrievedCount = Math.max(0, retrievedCount);
        chunkCount = Math.max(0, chunkCount);
        contextChars = Math.max(0, contextChars);
        citationCount = Math.max(0, citationCount);
    }

    public static RagDebugSnapshot empty() {
        return fromTrace(RagQaTrace.empty(), 0, false);
    }

    public static RagDebugSnapshot fromTrace(RagQaTrace trace) {
        return fromTrace(trace, 0, false);
    }

    public static RagDebugSnapshot fromContext(RagQaContext context) {
        if (context == null) {
            return empty();
        }
        return fromTrace(context.trace(), context.chunkCount(), false);
    }

    public static RagDebugSnapshot fromTrace(RagQaTrace trace, int chunkCount, boolean userIdPresent) {
        RagQaTrace resolvedTrace = trace == null ? RagQaTrace.empty() : trace;
        String vectorStoreType = resolvedTrace.vectorStoreType();
        return new RagDebugSnapshot(
                resolvedTrace.ragEnabled(),
                resolvedTrace.embeddingProvider(),
                vectorStoreType,
                vectorStoreType,
                resolvedTrace.documentIdPresent(),
                userIdPresent,
                resolvedTrace.topK(),
                resolvedTrace.retrievedCount(),
                chunkCount,
                resolvedTrace.indexReused(),
                resolvedTrace.indexTruncated(),
                resolvedTrace.contextChars(),
                resolvedTrace.contextTruncated(),
                resolvedTrace.contextHashPresent(),
                resolvedTrace.fallbackUsed(),
                resolvedTrace.fallbackReason(),
                resolvedTrace.citationCount(),
                resolvedTrace.cacheKeyRagAware()
        );
    }

    private static String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private static String safeReason(String reason) {
        String firstToken = safeText(reason).split("\\s+", 2)[0];
        String normalized = firstToken.replaceAll("[^a-zA-Z0-9_.-]+", "_");
        while (normalized.contains("__")) {
            normalized = normalized.replace("__", "_");
        }
        normalized = stripEdgeUnderscore(normalized);
        if (normalized.length() <= MAX_REASON_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_REASON_LENGTH);
    }

    private static String stripEdgeUnderscore(String value) {
        String result = value;
        while (result.startsWith("_")) {
            result = result.substring(1);
        }
        while (result.endsWith("_")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
