package com.docpilot.backend.memory.service;

import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.memory.constant.UserMemorySourceType;
import com.docpilot.backend.memory.constant.UserMemoryStatus;
import com.docpilot.backend.memory.constant.UserMemoryType;
import com.docpilot.backend.memory.entity.UserMemory;
import com.docpilot.backend.memory.mapper.UserMemoryMapper;
import com.docpilot.backend.memory.service.impl.UserMemoryServiceImpl;
import com.docpilot.backend.memory.vo.UserMemoryResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserMemoryServiceImplTest {

    private final UserMemoryMapper memoryMapper = mock(UserMemoryMapper.class);
    private final MemorySafetyValidator safetyValidator = mock(MemorySafetyValidator.class);
    private final MemoryExtractionService extractionService = mock(MemoryExtractionService.class);
    private final UserMemoryServiceImpl service = new UserMemoryServiceImpl(
            memoryMapper,
            safetyValidator,
            extractionService
    );

    @Test
    void shouldPersistExtractedSuggestionsAsSuggested() {
        when(extractionService.extractSuggestions(7L, 10L, 20)).thenReturn(List.of(
                new MemorySuggestionCandidate(UserMemoryType.ANSWER_STYLE,
                        "以后请回答时先给结论", 10L, 101L, 40, 0.7)
        ));
        when(memoryMapper.selectExistingCandidate(7L, UserMemoryType.ANSWER_STYLE,
                "以后请回答时先给结论")).thenReturn(null);
        when(memoryMapper.insert(any(UserMemory.class))).thenAnswer(invocation -> {
            UserMemory memory = invocation.getArgument(0);
            memory.setId(99L);
            return 1;
        });

        List<UserMemoryResponse> responses = service.extractSuggestions(7L, 10L, 20);

        ArgumentCaptor<UserMemory> captor = ArgumentCaptor.forClass(UserMemory.class);
        verify(memoryMapper).insert(captor.capture());
        UserMemory saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(UserMemoryStatus.SUGGESTED);
        assertThat(saved.getSourceType()).isEqualTo(UserMemorySourceType.SYSTEM_EXTRACTED);
        assertThat(saved.getSourceConversationId()).isEqualTo(10L);
        assertThat(saved.getSourceMessageId()).isEqualTo(101L);
        assertThat(saved.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.7));
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).status()).isEqualTo(UserMemoryStatus.SUGGESTED);
    }

    @Test
    void shouldReuseExistingSuggestedMemoryWithoutInsert() {
        UserMemory existing = memory(99L, UserMemoryStatus.SUGGESTED);
        when(extractionService.extractSuggestions(7L, 10L, null)).thenReturn(List.of(
                new MemorySuggestionCandidate(UserMemoryType.PREFERENCE,
                        "偏好中文回答", 10L, 101L, 40, 0.7)
        ));
        when(memoryMapper.selectExistingCandidate(7L, UserMemoryType.PREFERENCE, "偏好中文回答"))
                .thenReturn(existing);

        List<UserMemoryResponse> responses = service.extractSuggestions(7L, 10L, null);

        verify(memoryMapper, never()).insert(any(UserMemory.class));
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).memoryId()).isEqualTo(99L);
    }

    @Test
    void shouldAcceptSuggestedMemory() {
        UserMemory memory = memory(99L, UserMemoryStatus.SUGGESTED);
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(memory);
        when(memoryMapper.updateStatus(7L, 99L, UserMemoryStatus.SUGGESTED, UserMemoryStatus.ACTIVE)).thenReturn(1);

        UserMemoryResponse response = service.acceptSuggestion(7L, 99L);

        assertThat(response.status()).isEqualTo(UserMemoryStatus.ACTIVE);
    }

    @Test
    void shouldIgnoreSuggestedMemory() {
        UserMemory memory = memory(99L, UserMemoryStatus.SUGGESTED);
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(memory);
        when(memoryMapper.updateStatus(7L, 99L, UserMemoryStatus.SUGGESTED, UserMemoryStatus.IGNORED)).thenReturn(1);

        UserMemoryResponse response = service.ignoreSuggestion(7L, 99L);

        assertThat(response.status()).isEqualTo(UserMemoryStatus.IGNORED);
    }

    @Test
    void shouldRejectAcceptingActiveMemoryAsSuggestion() {
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(memory(99L, UserMemoryStatus.ACTIVE));

        assertThatThrownBy(() -> service.acceptSuggestion(7L, 99L))
                .isInstanceOf(BusinessException.class);
    }

    private UserMemory memory(Long id, String status) {
        UserMemory memory = new UserMemory();
        memory.setId(id);
        memory.setUserId(7L);
        memory.setMemoryType(UserMemoryType.PREFERENCE);
        memory.setContent("偏好中文回答");
        memory.setSourceType(UserMemorySourceType.SYSTEM_EXTRACTED);
        memory.setStatus(status);
        memory.setPriority(40);
        memory.setConfidence(BigDecimal.valueOf(0.7));
        return memory;
    }
}
