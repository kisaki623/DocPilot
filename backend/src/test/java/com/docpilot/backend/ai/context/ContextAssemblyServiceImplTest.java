package com.docpilot.backend.ai.context;

import com.docpilot.backend.ai.context.builder.KnowledgeBaseEvidenceBuilder;
import com.docpilot.backend.ai.context.builder.KnowledgeBaseEvidenceResult;
import com.docpilot.backend.ai.context.builder.RecentTurnsContextBuilder;
import com.docpilot.backend.ai.context.impl.ContextAssemblyServiceImpl;
import com.docpilot.backend.ai.context.memory.MemorySelector;
import com.docpilot.backend.ai.context.render.PromptRenderer;
import com.docpilot.backend.ai.context.security.ContextPermissionFilter;
import com.docpilot.backend.ai.context.token.TokenBudgetManager;
import com.docpilot.backend.ai.context.token.TokenEstimator;
import com.docpilot.backend.conversation.constant.ConversationContextMode;
import com.docpilot.backend.conversation.entity.Conversation;
import com.docpilot.backend.conversation.entity.ConversationSummary;
import com.docpilot.backend.conversation.service.ConversationService;
import com.docpilot.backend.conversation.service.ConversationSummaryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextAssemblyServiceImplTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final ConversationSummaryService summaryService = mock(ConversationSummaryService.class);
    private final RecentTurnsContextBuilder recentTurnsBuilder = mock(RecentTurnsContextBuilder.class);
    private final MemorySelector memorySelector = mock(MemorySelector.class);
    private final KnowledgeBaseEvidenceBuilder evidenceBuilder = mock(KnowledgeBaseEvidenceBuilder.class);
    private final TokenEstimator tokenEstimator = new TokenEstimator();
    private final ContextAssemblyServiceImpl service = new ContextAssemblyServiceImpl(
            conversationService,
            summaryService,
            recentTurnsBuilder,
            memorySelector,
            evidenceBuilder,
            new ContextPermissionFilter(),
            new TokenBudgetManager(),
            new PromptRenderer(),
            tokenEstimator
    );

    @Test
    void recentTurnsModeShouldNotUseMemorySummaryOrRag() {
        Conversation conversation = conversation(ConversationContextMode.RECENT_TURNS, null, false, false);
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation);
        when(recentTurnsBuilder.build(7L, 10L, 8)).thenReturn(List.of(recentItem()));
        when(evidenceBuilder.build(conversation, "继续说", ContextPolicy.forMode("RECENT_TURNS", null)))
                .thenReturn(KnowledgeBaseEvidenceResult.notTriggered());

        ContextAssemblyResult result = service.buildContext(new ContextAssemblyRequest(7L, 10L, "继续说", null));

        assertThat(result.trace().contextMode()).isEqualTo(ConversationContextMode.RECENT_TURNS);
        assertThat(result.trace().summaryUsed()).isFalse();
        assertThat(result.trace().memoryCount()).isZero();
        assertThat(result.trace().ragTriggered()).isFalse();
        verify(summaryService, never()).getActiveSummary(7L, 10L);
        verify(memorySelector, never()).select(7L, 0);
    }

    @Test
    void agentMemoryModeShouldUseMemorySummaryAndRagEvidence() {
        Conversation conversation = conversation(ConversationContextMode.AGENT_MEMORY, 3L, true, true);
        ConversationSummary summary = new ConversationSummary();
        summary.setId(20L);
        summary.setConversationId(10L);
        summary.setUserId(7L);
        summary.setSummary("summary");
        summary.setSummaryVersion(1);
        summary.setStatus("ACTIVE");
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation);
        when(summaryService.getActiveSummary(7L, 10L)).thenReturn(summary);
        when(memorySelector.select(7L, 5)).thenReturn(List.of(memoryItem()));
        when(recentTurnsBuilder.build(7L, 10L, 8)).thenReturn(List.of(recentItem()));
        when(evidenceBuilder.build(org.mockito.Mockito.eq(conversation), org.mockito.Mockito.eq("根据知识库总结"),
                org.mockito.Mockito.any())).thenReturn(new KnowledgeBaseEvidenceResult(
                true,
                true,
                false,
                "",
                List.of(evidenceItem()),
                List.of(),
                Map.of(101L, 1)
        ));

        ContextAssemblyResult result = service.buildContext(new ContextAssemblyRequest(7L, 10L, "根据知识库总结", null));

        assertThat(result.trace().contextMode()).isEqualTo(ConversationContextMode.AGENT_MEMORY);
        assertThat(result.trace().summaryUsed()).isTrue();
        assertThat(result.trace().memoryCount()).isEqualTo(1);
        assertThat(result.trace().ragTriggered()).isTrue();
        assertThat(result.trace().evidenceCount()).isEqualTo(1);
        assertThat(result.modelCallSkipped()).isFalse();
    }

    @Test
    void requiredRagNoEvidenceShouldSkipModelCall() {
        Conversation conversation = conversation(ConversationContextMode.AGENT_MEMORY, 3L, true, true);
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation);
        when(memorySelector.select(7L, 5)).thenReturn(List.of());
        when(recentTurnsBuilder.build(7L, 10L, 8)).thenReturn(List.of());
        when(evidenceBuilder.build(org.mockito.Mockito.eq(conversation), org.mockito.Mockito.eq("根据知识库回答"),
                org.mockito.Mockito.any())).thenReturn(new KnowledgeBaseEvidenceResult(
                true,
                true,
                true,
                "当前知识库中没有找到足够证据，无法基于知识库回答该问题。",
                List.of(),
                List.of(),
                Map.of()
        ));

        ContextAssemblyResult result = service.buildContext(new ContextAssemblyRequest(7L, 10L, "根据知识库回答", null));

        assertThat(result.modelCallSkipped()).isTrue();
        assertThat(result.fallbackAnswer()).contains("没有找到足够证据");
        assertThat(result.trace().fallbackReason()).isEqualTo("NO_EVIDENCE");
    }

    private Conversation conversation(String mode, Long boundKnowledgeBaseId, boolean summaryEnabled, boolean memoryEnabled) {
        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setUserId(7L);
        conversation.setContextMode(mode);
        conversation.setBoundKnowledgeBaseId(boundKnowledgeBaseId);
        conversation.setSummaryEnabled(summaryEnabled);
        conversation.setMemoryEnabled(memoryEnabled);
        conversation.setStatus("ACTIVE");
        return conversation;
    }

    private ContextItem memoryItem() {
        return new ContextItem(ContextType.MEMORY, "PREFERENCE: concise", 600, 4, false,
                7L, "1", "ACTIVE", Map.of("memoryType", "PREFERENCE"));
    }

    private ContextItem recentItem() {
        return new ContextItem(ContextType.RECENT_TURN, "USER: hi", 700, 4, false,
                7L, "1", "ACTIVE", Map.of());
    }

    private ContextItem evidenceItem() {
        return new ContextItem(ContextType.RAG_EVIDENCE, "doc evidence", 900, 4, false,
                7L, "v1", "ACTIVE", Map.of());
    }
}
