package com.docpilot.backend.ai.agent.tool.openai;

public record OpenAiToolDefinition(String type, OpenAiFunctionDefinition function) {

    public OpenAiToolDefinition {
        type = type == null || type.isBlank() ? "function" : type.trim();
        if (function == null) {
            throw new IllegalArgumentException("function must not be null");
        }
    }
}
