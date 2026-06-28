package com.docpilot.backend.memory.service;

import com.docpilot.backend.conversation.constant.ConversationMessageRole;
import com.docpilot.backend.conversation.entity.ConversationMessage;
import com.docpilot.backend.conversation.mapper.ConversationMessageMapper;
import com.docpilot.backend.conversation.service.ConversationService;
import com.docpilot.backend.memory.constant.UserMemoryType;
import com.docpilot.backend.memory.service.impl.RuleBasedMemoryExtractionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleBasedMemoryExtractionServiceTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final ConversationMessageMapper messageMapper = mock(ConversationMessageMapper.class);
    private final RuleBasedMemoryExtractionService service = new RuleBasedMemoryExtractionService(
            conversationService,
            messageMapper
    );

    @Test
    void shouldExtractSuggestionsFromUserMessagesOnly() {
        when(messageMapper.selectRecentActive(7L, 10L, 30)).thenReturn(List.of(
                message(101L, 1, ConversationMessageRole.USER, "以后请回答时先给结论，再解释取舍。"),
                message(102L, 2, ConversationMessageRole.ASSISTANT, "好的，我会记住。"),
                message(103L, 3, ConversationMessageRole.USER, "当前目标是完成 T013 的记忆候选机制。")
        ));

        List<MemorySuggestionCandidate> candidates = service.extractSuggestions(7L, 10L, null);

        verify(conversationService).requireOwnedActive(7L, 10L);
        assertThat(candidates).hasSize(2);
        assertThat(candidates).extracting(MemorySuggestionCandidate::memoryType)
                .containsExactly(UserMemoryType.ANSWER_STYLE, UserMemoryType.TASK_GOAL);
        assertThat(candidates).extracting(MemorySuggestionCandidate::sourceMessageId)
                .containsExactly(101L, 103L);
    }

    @Test
    void shouldNotExtractRagEvidenceFromAssistantMessagesAsMemory() {
        when(messageMapper.selectRecentActive(7L, 10L, 30)).thenReturn(List.of(
                message(101L, 1, ConversationMessageRole.USER, "根据知识库总结项目状态。"),
                message(102L, 2, ConversationMessageRole.ASSISTANT,
                        "RAG evidence: DocPilot 已完成 Qdrant 检索和知识库引用。"),
                message(103L, 3, ConversationMessageRole.ASSISTANT,
                        "引用来源显示当前任务已经实现，下一步是继续 RAG 质量门禁。")
        ));

        List<MemorySuggestionCandidate> candidates = service.extractSuggestions(7L, 10L, null);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).sourceMessageId()).isEqualTo(101L);
        assertThat(candidates.get(0).content()).doesNotContain("RAG evidence");
    }

    @Test
    void shouldExtractEnglishAnswerStyleAndTaskGoalForSmokeMessages() {
        when(messageMapper.selectRecentActive(7L, 10L, 30)).thenReturn(List.of(
                message(101L, 1, ConversationMessageRole.USER,
                        "Please answer with the conclusion first, then explain tradeoffs."),
                message(102L, 2, ConversationMessageRole.USER,
                        "Current goal is finishing the Memory Quality smoke phase.")
        ));

        List<MemorySuggestionCandidate> candidates = service.extractSuggestions(7L, 10L, null);

        assertThat(candidates).extracting(MemorySuggestionCandidate::memoryType)
                .containsExactly(UserMemoryType.ANSWER_STYLE, UserMemoryType.TASK_GOAL);
    }

    private ConversationMessage message(Long id, int sequenceNo, String role, String content) {
        ConversationMessage message = new ConversationMessage();
        message.setId(id);
        message.setConversationId(10L);
        message.setUserId(7L);
        message.setSequenceNo(sequenceNo);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
