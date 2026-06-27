package com.docpilot.backend.ai.context;

import java.util.List;
import java.util.LinkedHashMap;
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

    public Map<String, Integer> getContextSourceCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("conversationSummary", summaryUsed ? 1 : 0);
        counts.put("recentMessages", Math.max(0, recentMessageCount));
        counts.put("userMemory", Math.max(0, memoryCount));
        counts.put("ragEvidence", Math.max(0, evidenceCount));
        return counts;
    }

    public Map<String, Boolean> getContextSourceFlags() {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        flags.put("conversationContext", summaryUsed || recentMessageCount > 0);
        flags.put("userMemory", memoryUsed && memoryCount > 0);
        flags.put("ragEvidence", evidenceCount > 0);
        flags.put("ragRequired", ragRequired);
        flags.put("fallback", fallbackUsed || modelCallSkipped);
        return flags;
    }
}
