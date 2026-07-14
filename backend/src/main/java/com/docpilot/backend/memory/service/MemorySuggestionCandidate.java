package com.docpilot.backend.memory.service;

public record MemorySuggestionCandidate(
        String memoryType,
        String content,
        Long sourceConversationId,
        Long sourceMessageId,
        int priority,
        double confidence
) {
}
