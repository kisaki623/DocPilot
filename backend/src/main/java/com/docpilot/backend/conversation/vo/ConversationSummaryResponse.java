package com.docpilot.backend.conversation.vo;

import com.docpilot.backend.conversation.entity.ConversationSummary;

import java.time.LocalDateTime;

public record ConversationSummaryResponse(
        Long conversationId,
        String summary,
        Integer coveredStartSeq,
        Integer coveredEndSeq,
        Integer summaryVersion,
        String status,
        Integer tokenCount,
        LocalDateTime updatedAt
) {

    public static ConversationSummaryResponse empty(Long conversationId) {
        return new ConversationSummaryResponse(conversationId, null, null, null, null,
                "NOT_FOUND", 0, null);
    }

    public static ConversationSummaryResponse from(ConversationSummary summary) {
        if (summary == null) {
            return null;
        }
        return new ConversationSummaryResponse(
                summary.getConversationId(),
                summary.getSummary(),
                summary.getCoveredStartSeq(),
                summary.getCoveredEndSeq(),
                summary.getSummaryVersion(),
                summary.getStatus(),
                summary.getTokenCount(),
                summary.getUpdateTime()
        );
    }
}
