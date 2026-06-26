package com.docpilot.backend.ai.context;

import com.docpilot.backend.ai.context.token.TokenBudgetManager;
import com.docpilot.backend.ai.context.token.TokenBudgetResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBudgetManagerTest {

    private final TokenBudgetManager manager = new TokenBudgetManager();

    @Test
    void shouldKeepRequiredItemsAndDropOptionalWhenOverBudget() {
        ContextPolicy policy = new ContextPolicy("AGENT_MEMORY", 10, 8, 5, 1200, 6, 6000, 800,
                true, true, true);

        TokenBudgetResult result = manager.apply(List.of(
                item(ContextType.SYSTEM, 4, true, 1000),
                item(ContextType.CURRENT_MESSAGE, 4, true, 1000),
                item(ContextType.MEMORY, 8, false, 500)
        ), policy, false);

        assertThat(result.usedItems()).extracting(ContextItem::type)
                .contains(ContextType.SYSTEM, ContextType.CURRENT_MESSAGE)
                .doesNotContain(ContextType.MEMORY);
        assertThat(result.truncated()).isTrue();
        assertThat(result.truncatedTypes()).contains("MEMORY");
    }

    @Test
    void shouldPreferRequiredRagEvidenceForKnowledgeQuestion() {
        ContextPolicy policy = new ContextPolicy("AGENT_MEMORY", 18, 8, 5, 1200, 6, 6000, 800,
                true, true, true);

        TokenBudgetResult result = manager.apply(List.of(
                item(ContextType.SYSTEM, 4, true, 1000),
                item(ContextType.CURRENT_MESSAGE, 4, true, 1000),
                item(ContextType.MEMORY, 6, false, 700),
                item(ContextType.RAG_EVIDENCE, 6, false, 500)
        ), policy, true);

        assertThat(result.usedItems()).extracting(ContextItem::type)
                .contains(ContextType.RAG_EVIDENCE)
                .doesNotContain(ContextType.MEMORY);
    }

    private ContextItem item(ContextType type, int tokens, boolean required, int priority) {
        return new ContextItem(type, type.name(), priority, tokens, required, 7L, type.name(), "ACTIVE", Map.of());
    }
}
