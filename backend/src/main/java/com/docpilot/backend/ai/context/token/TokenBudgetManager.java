package com.docpilot.backend.ai.context.token;

import com.docpilot.backend.ai.context.ContextItem;
import com.docpilot.backend.ai.context.ContextPolicy;
import com.docpilot.backend.ai.context.ContextType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class TokenBudgetManager {

    public TokenBudgetResult apply(List<ContextItem> items, ContextPolicy policy, boolean ragRequired) {
        List<ContextItem> resolvedItems = items == null ? List.of() : items;
        int maxPromptTokens = policy.maxPromptTokens();
        List<ContextItem> requiredItems = resolvedItems.stream()
                .filter(ContextItem::required)
                .toList();
        List<ContextItem> optionalItems = resolvedItems.stream()
                .filter(item -> !item.required())
                .sorted(optionalComparator(ragRequired))
                .toList();

        List<ContextItem> used = new ArrayList<>();
        int tokens = 0;
        for (ContextItem item : requiredItems) {
            used.add(item);
            tokens += item.estimatedTokens();
        }

        Set<String> truncatedTypes = new LinkedHashSet<>();
        for (ContextItem item : optionalItems) {
            if (tokens + item.estimatedTokens() <= maxPromptTokens) {
                used.add(item);
                tokens += item.estimatedTokens();
                continue;
            }
            truncatedTypes.add(item.type().name());
        }

        used.sort(Comparator.comparingInt(this::renderOrder));
        return new TokenBudgetResult(used, tokens, !truncatedTypes.isEmpty(), List.copyOf(truncatedTypes));
    }

    private Comparator<ContextItem> optionalComparator(boolean ragRequired) {
        return Comparator
                .comparingInt((ContextItem item) -> effectivePriority(item, ragRequired))
                .reversed()
                .thenComparingInt(this::renderOrder);
    }

    private int effectivePriority(ContextItem item, boolean ragRequired) {
        if (ragRequired && item.type() == ContextType.RAG_EVIDENCE) {
            return 950;
        }
        return item.priority();
    }

    private int renderOrder(ContextItem item) {
        return switch (item.type()) {
            case SYSTEM -> 0;
            case MODE_INSTRUCTION -> 1;
            case MEMORY -> 2;
            case SUMMARY -> 3;
            case RECENT_TURN -> 4;
            case RAG_EVIDENCE -> 5;
            case CURRENT_MESSAGE -> 6;
            case OUTPUT_REQUIREMENT -> 7;
        };
    }
}
