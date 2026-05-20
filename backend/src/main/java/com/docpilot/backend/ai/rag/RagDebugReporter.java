package com.docpilot.backend.ai.rag;

import java.util.LinkedHashMap;
import java.util.Map;

public class RagDebugReporter {

    public Map<String, Object> toSafeMap(RagDebugSnapshot snapshot) {
        RagDebugSnapshot resolved = snapshot == null ? RagDebugSnapshot.empty() : snapshot;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ragEnabled", resolved.ragEnabled());
        fields.put("embeddingProvider", resolved.embeddingProvider());
        fields.put("vectorStoreProvider", resolved.vectorStoreProvider());
        fields.put("vectorStoreType", resolved.vectorStoreType());
        fields.put("documentIdPresent", resolved.documentIdPresent());
        fields.put("userIdPresent", resolved.userIdPresent());
        fields.put("topK", resolved.topK());
        fields.put("retrievedCount", resolved.retrievedCount());
        fields.put("chunkCount", resolved.chunkCount());
        fields.put("indexReused", resolved.indexReused());
        fields.put("indexTruncated", resolved.indexTruncated());
        fields.put("contextChars", resolved.contextChars());
        fields.put("contextTruncated", resolved.contextTruncated());
        fields.put("contextHashPresent", resolved.contextHashPresent());
        fields.put("fallbackUsed", resolved.fallbackUsed());
        fields.put("fallbackReason", resolved.fallbackReason());
        fields.put("citationCount", resolved.citationCount());
        fields.put("cacheKeyRagAware", resolved.cacheKeyRagAware());
        return fields;
    }

    public String format(RagDebugSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        toSafeMap(snapshot).forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(key).append("=").append(value);
        });
        return builder.toString();
    }
}
