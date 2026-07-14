package com.docpilot.backend.ai.context;

public record PromptMessage(String role, String content) {

    public PromptMessage {
        role = role == null || role.isBlank() ? "user" : role.trim();
        content = content == null ? "" : content.trim();
    }
}
