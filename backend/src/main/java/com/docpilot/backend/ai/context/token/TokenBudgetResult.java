package com.docpilot.backend.ai.context.token;

import com.docpilot.backend.ai.context.ContextItem;

import java.util.List;

public record TokenBudgetResult(
        List<ContextItem> usedItems,
        int estimatedPromptTokens,
        boolean truncated,
        List<String> truncatedTypes
) {

    public TokenBudgetResult {
        usedItems = usedItems == null ? List.of() : List.copyOf(usedItems);
        truncatedTypes = truncatedTypes == null ? List.of() : List.copyOf(truncatedTypes);
    }
}
