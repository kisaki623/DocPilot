package com.docpilot.backend.ai.context.token;

import com.docpilot.backend.ai.context.ContextItem;

import java.util.List;

public record TokenBudgetResult(
        List<ContextItem> usedItems,
        int estimatedPromptTokens,
        boolean truncated,
        List<String> truncatedTypes,
        List<ContextItem> droppedItems
) {

    public TokenBudgetResult(List<ContextItem> usedItems,
                             int estimatedPromptTokens,
                             boolean truncated,
                             List<String> truncatedTypes) {
        this(usedItems, estimatedPromptTokens, truncated, truncatedTypes, List.of());
    }

    public TokenBudgetResult {
        usedItems = usedItems == null ? List.of() : List.copyOf(usedItems);
        truncatedTypes = truncatedTypes == null ? List.of() : List.copyOf(truncatedTypes);
        droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
    }
}
