package com.docpilot.backend.memory.vo;

import com.docpilot.backend.memory.entity.UserMemory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserMemoryResponse(
        Long memoryId,
        String memoryType,
        String content,
        String sourceType,
        Long sourceConversationId,
        Long sourceMessageId,
        String status,
        Integer priority,
        BigDecimal confidence,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserMemoryResponse from(UserMemory memory) {
        if (memory == null) {
            return null;
        }
        return new UserMemoryResponse(
                memory.getId(),
                memory.getMemoryType(),
                memory.getContent(),
                memory.getSourceType(),
                memory.getSourceConversationId(),
                memory.getSourceMessageId(),
                memory.getStatus(),
                memory.getPriority(),
                memory.getConfidence(),
                memory.getCreateTime(),
                memory.getUpdateTime()
        );
    }
}
