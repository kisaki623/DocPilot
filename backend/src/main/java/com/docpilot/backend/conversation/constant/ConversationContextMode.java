package com.docpilot.backend.conversation.constant;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;

import java.util.Locale;

public final class ConversationContextMode {

    public static final String RECENT_TURNS = "RECENT_TURNS";
    public static final String AGENT_MEMORY = "AGENT_MEMORY";

    private ConversationContextMode() {
    }

    public static String normalizeOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return RECENT_TURNS;
        }
        return normalize(value);
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_CONTEXT_MODE);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!RECENT_TURNS.equals(normalized) && !AGENT_MEMORY.equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_CONTEXT_MODE);
        }
        return normalized;
    }

    public static boolean isAgentMemory(String value) {
        return AGENT_MEMORY.equals(normalizeOrDefault(value));
    }
}
