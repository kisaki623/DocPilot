package com.docpilot.backend.ai.context;

import java.util.Locale;

public enum GroundingPolicy {
    MODEL_ONLY,
    AUTO_RAG,
    STRICT_KB,
    LEGACY_UNSPECIFIED;

    public static GroundingPolicy normalizeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return GroundingPolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static GroundingPolicy resolveDefault(String requestedPolicy, boolean hasBoundKnowledgeBase) {
        if (!hasBoundKnowledgeBase) {
            return MODEL_ONLY;
        }
        GroundingPolicy parsed = normalizeOrNull(requestedPolicy);
        if (parsed != null && parsed != LEGACY_UNSPECIFIED) {
            return parsed;
        }
        return hasBoundKnowledgeBase ? AUTO_RAG : MODEL_ONLY;
    }
}
