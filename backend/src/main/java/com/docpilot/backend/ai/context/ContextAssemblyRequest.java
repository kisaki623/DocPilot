package com.docpilot.backend.ai.context;

public record ContextAssemblyRequest(
        Long userId,
        Long conversationId,
        String currentMessage,
        Integer maxPromptTokens,
        String groundingPolicy
) {

    public ContextAssemblyRequest(Long userId,
                                  Long conversationId,
                                  String currentMessage,
                                  Integer maxPromptTokens) {
        this(userId, conversationId, currentMessage, maxPromptTokens, null);
    }

    public ContextAssemblyRequest {
        currentMessage = currentMessage == null ? "" : currentMessage.trim();
        groundingPolicy = groundingPolicy == null ? "" : groundingPolicy.trim();
    }
}
