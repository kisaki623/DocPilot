package com.docpilot.backend.ai.context;

import java.util.Map;

public record ContextItem(
        ContextType type,
        String content,
        int priority,
        int estimatedTokens,
        boolean required,
        Long ownerUserId,
        String sourceId,
        String status,
        Map<String, Object> metadata
) {

    public ContextItem {
        content = content == null ? "" : content.trim();
        sourceId = sourceId == null ? "" : sourceId.trim();
        status = status == null ? "" : status.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        estimatedTokens = Math.max(0, estimatedTokens);
    }

    public boolean activeOrSystem() {
        return status.isBlank() || "ACTIVE".equals(status);
    }
}
