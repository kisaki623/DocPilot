package com.docpilot.backend.ai.agent.tool.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ToolResultSchema(String type, Map<String, String> properties) {

    public ToolResultSchema {
        type = requireNonBlank(type, "type");
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static ToolResultSchema object(Map<String, String> properties) {
        return new ToolResultSchema("object", properties);
    }

    public String toSchemaText() {
        return ToolParameterSchema.toJsonObject(properties);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
