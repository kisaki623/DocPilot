package com.docpilot.backend.ai.agent.tool;

public record ToolDefinition(String toolName,
                             String displayName,
                             String description,
                             String inputSchemaText,
                             String outputSchemaText,
                             boolean safeForLlmSelection) {

    public ToolDefinition {
        toolName = requireNonBlank(toolName, "toolName");
        displayName = requireNonBlank(displayName, "displayName");
        description = requireNonBlank(description, "description");
        inputSchemaText = requireNonBlank(inputSchemaText, "inputSchemaText");
        outputSchemaText = requireNonBlank(outputSchemaText, "outputSchemaText");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
