package com.docpilot.backend.ai.rag;

import java.util.LinkedHashMap;
import java.util.Map;

public class RagQaTraceFormatter {

    public Map<String, Object> toSafeMap(RagQaTrace trace) {
        RagQaTrace resolvedTrace = trace == null ? RagQaTrace.empty() : trace;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ragEnabled", resolvedTrace.ragEnabled());
        fields.put("embeddingProvider", resolvedTrace.embeddingProvider());
        fields.put("vectorStoreType", resolvedTrace.vectorStoreType());
        fields.put("documentIdPresent", resolvedTrace.documentIdPresent());
        fields.put("topK", resolvedTrace.topK());
        fields.put("retrievedCount", resolvedTrace.retrievedCount());
        fields.put("maxContextChars", resolvedTrace.maxContextChars());
        fields.put("contextChars", resolvedTrace.contextChars());
        fields.put("contextTruncated", resolvedTrace.contextTruncated());
        fields.put("contextHashPresent", resolvedTrace.contextHashPresent());
        fields.put("fallbackUsed", resolvedTrace.fallbackUsed());
        fields.put("fallbackReason", resolvedTrace.fallbackReason());
        fields.put("citationCount", resolvedTrace.citationCount());
        fields.put("cacheKeyRagAware", resolvedTrace.cacheKeyRagAware());
        fields.put("indexReused", resolvedTrace.indexReused());
        fields.put("indexTruncated", resolvedTrace.indexTruncated());
        return fields;
    }

    public String format(RagQaTrace trace) {
        StringBuilder builder = new StringBuilder();
        toSafeMap(trace).forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(key).append("=").append(value);
        });
        return builder.toString();
    }
}
