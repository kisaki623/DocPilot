package com.docpilot.backend.ai.context;

import com.docpilot.backend.ai.context.render.PromptRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptRendererTest {

    private final PromptRenderer renderer = new PromptRenderer();

    @Test
    void shouldRenderCurrentMessageAfterEvidence() {
        String context = renderer.renderContext(List.of(
                item(ContextType.SYSTEM, "system"),
                item(ContextType.MEMORY, "memory"),
                item(ContextType.SUMMARY, "summary"),
                item(ContextType.RECENT_TURN, "recent"),
                item(ContextType.RAG_EVIDENCE, "evidence"),
                item(ContextType.CURRENT_MESSAGE, "current")
        ));

        assertThat(context).containsSubsequence(
                "[System]",
                "[User Memories]",
                "[Conversation Summary]",
                "[Recent Turns]",
                "[Knowledge Base Evidence]",
                "[Current User Message]"
        );
    }

    private ContextItem item(ContextType type, String content) {
        return new ContextItem(type, content, 1, 1, true, 7L, type.name(), "ACTIVE", Map.of());
    }
}
