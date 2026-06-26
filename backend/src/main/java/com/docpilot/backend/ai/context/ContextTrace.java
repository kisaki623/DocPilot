package com.docpilot.backend.ai.context;

import java.util.List;
import java.util.Map;

public record ContextTrace(
        Long conversationId,
        Long messageId,
        String contextMode,
        boolean summaryUsed,
        int recentTurnCount,
        int recentMessageCount,
        boolean memoryUsed,
        int memoryCount,
        List<String> memoryTypes,
        boolean ragTriggered,
        boolean ragRequired,
        Long knowledgeBaseId,
        int evidenceCount,
        boolean noEvidence,
        Map<Long, Integer> documentHitCounts,
        int maxPromptTokens,
        int estimatedPromptTokens,
        boolean truncated,
        List<String> truncatedTypes,
        boolean fallbackUsed,
        String fallbackReason,
        boolean modelCallSkipped
) {

    public ContextTrace {
        memoryTypes = memoryTypes == null ? List.of() : List.copyOf(memoryTypes);
        documentHitCounts = documentHitCounts == null ? Map.of() : Map.copyOf(documentHitCounts);
        truncatedTypes = truncatedTypes == null ? List.of() : List.copyOf(truncatedTypes);
        fallbackReason = fallbackReason == null ? "" : fallbackReason.trim();
    }

    public ContextTrace withMessageId(Long resolvedMessageId) {
        return new ContextTrace(
                conversationId,
                resolvedMessageId,
                contextMode,
                summaryUsed,
                recentTurnCount,
                recentMessageCount,
                memoryUsed,
                memoryCount,
                memoryTypes,
                ragTriggered,
                ragRequired,
                knowledgeBaseId,
                evidenceCount,
                noEvidence,
                documentHitCounts,
                maxPromptTokens,
                estimatedPromptTokens,
                truncated,
                truncatedTypes,
                fallbackUsed,
                fallbackReason,
                modelCallSkipped
        );
    }
}
