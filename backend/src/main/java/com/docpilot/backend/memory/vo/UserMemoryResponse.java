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
        Long duplicateOfId,
        Long conflictWithId,
        String governanceHint,
        BigDecimal similarityScore,
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
                null,
                null,
                "",
                null,
                memory.getCreateTime(),
                memory.getUpdateTime()
        );
    }

    public static UserMemoryResponse from(UserMemory memory,
                                          Long duplicateOfId,
                                          Long conflictWithId,
                                          String governanceHint,
                                          BigDecimal similarityScore) {
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
                duplicateOfId,
                conflictWithId,
                governanceHint == null ? "" : governanceHint,
                similarityScore,
                memory.getCreateTime(),
                memory.getUpdateTime()
        );
    }
}
