package com.docpilot.backend.ai.agent.tool.openai;

public record OpenAiToolMessage(String role,
                                String toolCallId,
                                String name,
                                String content) {

    public OpenAiToolMessage {
        role = role == null || role.isBlank() ? "tool" : role.trim();
        toolCallId = requireNonBlank(toolCallId, "toolCallId");
        name = requireNonBlank(name, "name");
        content = content == null ? "" : content;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
