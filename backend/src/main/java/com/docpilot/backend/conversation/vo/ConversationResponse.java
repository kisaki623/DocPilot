package com.docpilot.backend.conversation.vo;

import com.docpilot.backend.conversation.entity.Conversation;

import java.time.LocalDateTime;

public record ConversationResponse(
        Long conversationId,
        String title,
        String contextMode,
        String status,
        Long boundKnowledgeBaseId,
        boolean summaryEnabled,
        boolean memoryEnabled,
        LocalDateTime lastMessageTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ConversationResponse from(Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getContextMode(),
                conversation.getStatus(),
                conversation.getBoundKnowledgeBaseId(),
                Boolean.TRUE.equals(conversation.getSummaryEnabled()),
                Boolean.TRUE.equals(conversation.getMemoryEnabled()),
                conversation.getLastMessageTime(),
                conversation.getCreateTime(),
                conversation.getUpdateTime()
        );
    }
}
