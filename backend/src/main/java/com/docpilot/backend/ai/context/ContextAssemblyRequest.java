package com.docpilot.backend.ai.context;

public record ContextAssemblyRequest(
        Long userId,
        Long conversationId,
        String currentMessage,
        Integer maxPromptTokens
) {

    public ContextAssemblyRequest {
        currentMessage = currentMessage == null ? "" : currentMessage.trim();
    }
}
