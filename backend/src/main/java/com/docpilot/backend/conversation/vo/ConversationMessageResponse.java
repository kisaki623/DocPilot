package com.docpilot.backend.conversation.vo;

import com.docpilot.backend.ai.context.ContextTrace;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;
import com.docpilot.backend.conversation.entity.ConversationMessage;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationMessageResponse(
        Long messageId,
        Long conversationId,
        String role,
        String content,
        Integer sequenceNo,
        Integer tokenCount,
        LocalDateTime createdAt,
        List<KnowledgeBaseRagEvidenceCitation> citations,
        ContextTrace contextTrace
) {

    public static ConversationMessageResponse from(ConversationMessage message) {
        return from(message, List.of(), null);
    }

    public static ConversationMessageResponse from(ConversationMessage message,
                                                   List<KnowledgeBaseRagEvidenceCitation> citations,
                                                   ContextTrace contextTrace) {
        if (message == null) {
            return null;
        }
        return new ConversationMessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getRole(),
                message.getContent(),
                message.getSequenceNo(),
                message.getTokenCount(),
                message.getCreateTime(),
                citations == null ? List.of() : List.copyOf(citations),
                contextTrace
        );
    }
}
