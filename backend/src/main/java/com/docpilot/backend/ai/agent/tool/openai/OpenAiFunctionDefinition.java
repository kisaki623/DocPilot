package com.docpilot.backend.ai.agent.tool.openai;

public record OpenAiFunctionDefinition(String name,
                                       String description,
                                       OpenAiFunctionParameters parameters) {

    public OpenAiFunctionDefinition {
        name = requireNonBlank(name, "name");
        description = description == null ? "" : description.trim();
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
