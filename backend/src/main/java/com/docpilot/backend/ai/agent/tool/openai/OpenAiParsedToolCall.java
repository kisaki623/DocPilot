package com.docpilot.backend.ai.agent.tool.openai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record OpenAiParsedToolCall(String id, String toolName, Map<String, Object> arguments) {

    public OpenAiParsedToolCall {
        id = requireNonBlank(id, "id");
        toolName = requireNonBlank(toolName, "toolName");
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
