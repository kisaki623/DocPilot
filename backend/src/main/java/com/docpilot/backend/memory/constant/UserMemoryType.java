package com.docpilot.backend.memory.constant;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;

import java.util.Locale;
import java.util.Set;

public final class UserMemoryType {

    public static final String PREFERENCE = "PREFERENCE";
    public static final String PROJECT_STATE = "PROJECT_STATE";
    public static final String TASK_GOAL = "TASK_GOAL";
    public static final String TECH_CONTEXT = "TECH_CONTEXT";
    public static final String ANSWER_STYLE = "ANSWER_STYLE";
    public static final String CUSTOM = "CUSTOM";

    private static final Set<String> VALID_TYPES = Set.of(
            PREFERENCE,
            PROJECT_STATE,
            TASK_GOAL,
            TECH_CONTEXT,
            ANSWER_STYLE,
            CUSTOM
    );

    private UserMemoryType() {
    }

    public static String normalizeOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return CUSTOM;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "memoryType is invalid");
        }
        return normalized;
    }
}
