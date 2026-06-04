package com.docpilot.backend.ai.agent.tool.openai;

public record OpenAiFunctionParameterProperty(String type, String description) {

    public OpenAiFunctionParameterProperty {
        type = type == null || type.isBlank() ? "string" : type.trim();
        description = description == null ? "" : description.trim();
    }
}
