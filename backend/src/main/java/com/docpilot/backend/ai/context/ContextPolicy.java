package com.docpilot.backend.ai.context;

import com.docpilot.backend.conversation.constant.ConversationContextMode;

public record ContextPolicy(
        String contextMode,
        int maxPromptTokens,
        int recentTurnsMaxRounds,
        int memoryMaxCount,
        int summaryMaxTokens,
        int ragEvidenceMaxCount,
        int ragEvidenceMaxTokens,
        int singleEvidenceMaxTokens,
        boolean summaryEnabled,
        boolean memoryEnabled,
        boolean ragEnabled
) {

    public static ContextPolicy forMode(String mode, Integer requestedMaxPromptTokens) {
        String resolved = ConversationContextMode.normalizeOrDefault(mode);
        int maxPromptTokens = requestedMaxPromptTokens == null || requestedMaxPromptTokens <= 0
                ? 12_000
                : Math.min(24_000, requestedMaxPromptTokens);
        if (ConversationContextMode.AGENT_MEMORY.equals(resolved)) {
            return new ContextPolicy(resolved, maxPromptTokens, 8, 5, 1_200, 6, 6_000, 800,
                    true, true, true);
        }
        return new ContextPolicy(resolved, Math.min(maxPromptTokens, 8_000), 8, 0, 0, 0, 0, 0,
                false, false, false);
    }
}
