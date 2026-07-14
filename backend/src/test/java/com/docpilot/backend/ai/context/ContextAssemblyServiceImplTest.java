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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.any;
import static org.mockito.ArgumentMatchers.anyInt;

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
        when(evidenceBuilder.build(eq(conversation), eq("继续说"), any(ContextPolicy.class), eq(GroundingPolicy.MODEL_ONLY)))
                .thenReturn(KnowledgeBaseEvidenceResult.notTriggered(RouteDecision.MODEL_ONLY));

        ContextAssemblyResult result = service.buildContext(new ContextAssemblyRequest(7L, 10L, "继续说", null));

        assertThat(result.trace().contextMode()).isEqualTo(ConversationContextMode.RECENT_TURNS);
        assertThat(result.trace().groundingPolicy()).isEqualTo(GroundingPolicy.MODEL_ONLY.name());
        assertThat(result.trace().routeDecision()).isEqualTo(RouteDecision.MODEL_ONLY.name());
        assertThat(result.trace().summaryUsed()).isFalse();
        assertThat(result.trace().memoryCount()).isZero();
        assertThat(result.trace().ragTriggered()).isFalse();
        verify(summaryService, never()).getActiveSummary(7L, 10L);
        verify(memorySelector, never()).select(7L, 0);
    }

    @Test
    void agentMemoryModeWithMemoryDisabledShouldNotSelectLongTermMemory() {
        Conversation conversation = conversation(ConversationContextMode.AGENT_MEMORY, null, true, false);
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation);
        when(summaryService.getActiveSummary(7L, 10L)).thenReturn(null);
        when(recentTurnsBuilder.build(7L, 10L, 8)).thenReturn(List.of(recentItem()));
        when(evidenceBuilder.build(eq(conversation), eq("继续说"), any(ContextPolicy.class), eq(GroundingPolicy.MODEL_ONLY)))
                .thenReturn(KnowledgeBaseEvidenceResult.notTriggered(RouteDecision.MODEL_ONLY));

        ContextAssemblyResult result = service.buildContext(new ContextAssemblyRequest(7L, 10L, "继续说", null));

        assertThat(result.trace().contextMode()).isEqualTo(ConversationContextMode.AGENT_MEMORY);
        assertThat(result.trace().summaryUsed()).isFalse();
        assertThat(result.trace().memoryUsed()).isFalse();
        assertThat(result.trace().memoryCount()).isZero();
        verify(memorySelector, never()).select(any(), anyInt());
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
        when(evidenceBuilder.build(eq(conversation), eq("根据知识库总结"),
                any(ContextPolicy.class), eq(GroundingPolicy.AUTO_RAG))).thenReturn(new KnowledgeBaseEvidenceResult(
                true,
                false,
                false,
                "",
                List.of(evidenceItem()),
                List.of(),
                Map.of(101L, 1),
                RouteDecision.AUTO_RAG_EVIDENCE
        ));

        ContextAssemblyResult result = service.buildContext(new ContextAssemblyRequest(7L, 10L, "根据知识库总结", null));

        assertThat(result.trace().contextMode()).isEqualTo(ConversationContextMode.AGENT_MEMORY);
        assertThat(result.trace().groundingPolicy()).isEqualTo(GroundingPolicy.AUTO_RAG.name());
        assertThat(result.trace().routeDecision()).isEqualTo(RouteDecision.AUTO_RAG_EVIDENCE.name());
        assertThat(result.trace().summaryUsed()).isTrue();
        assertThat(result.trace().memoryCount()).isEqualTo(1);
        assertThat(result.trace().ragTriggered()).isTrue();
        assertThat(result.trace().evidenceCount()).isEqualTo(1);
        assertThat(result.trace().getContextSourceCounts())
                .containsEntry("conversationSummary", 1)
                .containsEntry("recentMessages", 1)
                .containsEntry("userMemory", 1)
                .containsEntry("ragEvidence", 1);
        assertThat(result.trace().getContextSourceFlags())
                .containsEntry("conversationContext", true)
                .containsEntry("userMemory", true)
                .containsEntry("ragEvidence", true);
        assertThat(result.trace().technicalDetails().available()).isTrue();
        assertThat(result.trace().technicalDetails().traceId()).isEqualTo("ctx-10-pending");
        assertThat(result.trace().technicalDetails().route().routeDecision())
                .isEqualTo(RouteDecision.AUTO_RAG_EVIDENCE.name());
        assertThat(result.trace().technicalDetails().timingsMs())
                .containsKeys("summary", "memory", "recentTurns", "retrieval", "tokenBudget", "contextAssembly");
        assertThat(result.trace().technicalDetails().tokenBudget().byType())
                .extracting(ContextTraceTechnicalDetails.TokenBudgetTypeSummary::type)
                .contains(ContextType.RAG_EVIDENCE.name());
        assertThat(result.trace().technicalDetails().contextUsage().memory().types()).contains("PREFERENCE");
        assertThat(result.modelCallSkipped()).isFalse();
    }

    @Test
    void autoRagNoEvidenceShouldFallbackToModelCall() {
        Conversation conversation = conversation(ConversationContextMode.AGENT_MEMORY, 3L, true, true);
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation);
        when(memorySelector.select(7L, 5)).thenReturn(List.of());
        when(recentTurnsBuilder.build(7L, 10L, 8)).thenReturn(List.of());
        when(evidenceBuilder.build(eq(conversation), eq("根据知识库回答"),
                any(ContextPolicy.class), eq(GroundingPolicy.AUTO_RAG))).thenReturn(new KnowledgeBaseEvidenceResult(
                true,
                false,
                true,
                "",
                List.of(),
                List.of(),
                Map.of(),
                RouteDecision.AUTO_NO_EVIDENCE_MODEL
        ));

        ContextAssemblyResult result = service.buildContext(new ContextAssemblyRequest(7L, 10L, "根据知识库回答", null));

        assertThat(result.modelCallSkipped()).isFalse();
        assertThat(result.fallbackAnswer()).isBlank();
        assertThat(result.ragRequired()).isFalse();
        assertThat(result.trace().routeDecision()).isEqualTo(RouteDecision.AUTO_NO_EVIDENCE_MODEL.name());
        assertThat(result.trace().fallbackReason()).isBlank();
    }

    @Test
    void autoRagRequiredNoEvidenceShouldSkipModelCall() {
        Conversation conversation = conversation(ConversationContextMode.AGENT_MEMORY, 3L, true, true);
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation);
        when(memorySelector.select(7L, 5)).thenReturn(List.of());
        when(recentTurnsBuilder.build(7L, 10L, 8)).thenReturn(List.of());
        when(evidenceBuilder.build(eq(conversation), eq("请引用文档回答"),
                any(ContextPolicy.class), eq(GroundingPolicy.AUTO_RAG))).thenReturn(new KnowledgeBaseEvidenceResult(
                true,
                true,
                true,
                "当前知识库中没有找到足够证据，无法基于知识库回答该问题。",
                List.of(),
                List.of(),
                Map.of(),
                RouteDecision.AUTO_REQUIRED_NO_EVIDENCE_FALLBACK
        ));

        ContextAssemblyResult result = service.buildContext(new ContextAssemblyRequest(7L, 10L, "请引用文档回答", null));

        assertThat(result.modelCallSkipped()).isTrue();
        assertThat(result.fallbackAnswer()).contains("没有找到足够证据");
        assertThat(result.ragRequired()).isTrue();
        assertThat(result.trace().routeDecision()).isEqualTo(RouteDecision.AUTO_REQUIRED_NO_EVIDENCE_FALLBACK.name());
        assertThat(result.trace().fallbackReason()).isEqualTo("REQUIRED_EVIDENCE_NO_EVIDENCE");
    }

    @Test
    void strictKbNoEvidenceShouldSkipModelCall() {
        Conversation conversation = conversation(ConversationContextMode.AGENT_MEMORY, 3L, true, true);
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation);
        when(memorySelector.select(7L, 5)).thenReturn(List.of());
        when(recentTurnsBuilder.build(7L, 10L, 8)).thenReturn(List.of());
        when(evidenceBuilder.build(eq(conversation), eq("只根据知识库回答"),
                any(ContextPolicy.class), eq(GroundingPolicy.STRICT_KB))).thenReturn(new KnowledgeBaseEvidenceResult(
                true,
                true,
                true,
                "当前知识库中没有找到足够证据，无法基于知识库回答该问题。",
                List.of(),
                List.of(),
                Map.of(),
                RouteDecision.STRICT_NO_EVIDENCE_FALLBACK
        ));

        ContextAssemblyResult result = service.buildContext(new ContextAssemblyRequest(
                7L,
                10L,
                "只根据知识库回答",
                null,
                GroundingPolicy.STRICT_KB.name()
        ));

        assertThat(result.modelCallSkipped()).isTrue();
        assertThat(result.fallbackAnswer()).contains("没有找到足够证据");
        assertThat(result.trace().routeDecision()).isEqualTo(RouteDecision.STRICT_NO_EVIDENCE_FALLBACK.name());
        assertThat(result.trace().fallbackReason()).isEqualTo("STRICT_KB_NO_EVIDENCE");
    }

    @Test
    void recentTurnsWithStrictKbShouldStillTriggerRagWithoutEnablingMemory() {
        Conversation conversation = conversation(ConversationContextMode.RECENT_TURNS, 3L, false, false);
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation);
        when(recentTurnsBuilder.build(7L, 10L, 8)).thenReturn(List.of(recentItem()));
        when(evidenceBuilder.build(eq(conversation), eq("只根据知识库回答"),
                any(ContextPolicy.class), eq(GroundingPolicy.STRICT_KB))).thenReturn(new KnowledgeBaseEvidenceResult(
                true,
                true,
                false,
                "",
                List.of(evidenceItem()),
                List.of(),
                Map.of(101L, 1),
                RouteDecision.STRICT_KB_EVIDENCE
        ));

        ContextAssemblyResult result = service.buildContext(new ContextAssemblyRequest(
                7L,
                10L,
                "只根据知识库回答",
                null,
                GroundingPolicy.STRICT_KB.name()
        ));

        assertThat(result.trace().contextMode()).isEqualTo(ConversationContextMode.RECENT_TURNS);
        assertThat(result.trace().groundingPolicy()).isEqualTo(GroundingPolicy.STRICT_KB.name());
        assertThat(result.trace().routeDecision()).isEqualTo(RouteDecision.STRICT_KB_EVIDENCE.name());
        assertThat(result.trace().memoryUsed()).isFalse();
        assertThat(result.trace().ragTriggered()).isTrue();
        assertThat(result.trace().evidenceCount()).isEqualTo(1);
        assertThat(result.promptMessages())
                .extracting(PromptMessage::content)
                .noneMatch(content -> content.contains("Do not rely on long-term memory or knowledge-base evidence"));
        verify(memorySelector, never()).select(7L, 0);
    }

    @Test
    void strictKbRequestWithoutBoundKnowledgeBaseShouldResolveToModelOnly() {
        Conversation conversation = conversation(ConversationContextMode.AGENT_MEMORY, null, true, true);
        when(conversationService.requireOwnedActive(7L, 10L)).thenReturn(conversation);
        when(summaryService.getActiveSummary(7L, 10L)).thenReturn(null);
        when(memorySelector.select(7L, 5)).thenReturn(List.of(memoryItem()));
        when(recentTurnsBuilder.build(7L, 10L, 8)).thenReturn(List.of());
        when(evidenceBuilder.build(eq(conversation), eq("普通问题"),
                any(ContextPolicy.class), eq(GroundingPolicy.MODEL_ONLY)))
                .thenReturn(KnowledgeBaseEvidenceResult.notTriggered(RouteDecision.MODEL_ONLY));

        ContextAssemblyResult result = service.buildContext(new ContextAssemblyRequest(
                7L,
                10L,
                "普通问题",
                null,
                GroundingPolicy.STRICT_KB.name()
        ));

        assertThat(result.trace().groundingPolicy()).isEqualTo(GroundingPolicy.MODEL_ONLY.name());
        assertThat(result.trace().routeDecision()).isEqualTo(RouteDecision.MODEL_ONLY.name());
        assertThat(result.ragTriggered()).isFalse();
        assertThat(result.ragRequired()).isFalse();
        assertThat(result.modelCallSkipped()).isFalse();
        assertThat(result.trace().memoryUsed()).isTrue();
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
