package com.docpilot.backend.ai.agent.tool.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ToolParameterSchema(String type, Map<String, String> properties) {

    public ToolParameterSchema {
        type = requireNonBlank(type, "type");
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static ToolParameterSchema object(Map<String, String> properties) {
        return new ToolParameterSchema("object", properties);
    }

    public String toSchemaText() {
        return toJsonObject(properties);
    }

    static String toJsonObject(Map<String, String> values) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append('"')
                    .append(':')
                    .append('"').append(escape(entry.getValue())).append('"');
        }
        builder.append('}');
        return builder.toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
