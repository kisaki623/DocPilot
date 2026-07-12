package com.docpilot.backend.memory.service;

import com.docpilot.backend.common.error.ErrorCode;
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
import static org.mockito.Mockito.doThrow;
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
        when(memoryMapper.selectActiveByUser(7L, UserMemoryType.PREFERENCE, 100)).thenReturn(List.of());
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
    void shouldUpdateActiveMemory() {
        UserMemory memory = memory(99L, UserMemoryStatus.ACTIVE);
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(memory);
        when(memoryMapper.selectActiveByUser(7L, UserMemoryType.PREFERENCE, 100)).thenReturn(List.of(memory));
        when(memoryMapper.updateContentAndPriority(7L, 99L, UserMemoryStatus.ACTIVE, "偏好先给结论", 55))
                .thenReturn(1);

        UserMemoryResponse response = service.update(7L, 99L, "  偏好先给结论  ", 55);

        assertThat(response.content()).isEqualTo("偏好先给结论");
        assertThat(response.priority()).isEqualTo(55);
    }

    @Test
    void shouldRejectEditingSuggestedMemory() {
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(memory(99L, UserMemoryStatus.SUGGESTED));

        assertThatThrownBy(() -> service.update(7L, 99L, "偏好先给结论", 55))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only active memory can be edited");
    }

    @Test
    void shouldRejectEditingIntoDuplicateActiveMemory() {
        UserMemory memory = memory(99L, UserMemoryStatus.ACTIVE);
        memory.setContent("偏好英文回答");
        UserMemory duplicate = memory(88L, UserMemoryStatus.ACTIVE);
        duplicate.setContent("偏好中文回答");
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(memory);
        when(memoryMapper.selectActiveByUser(7L, UserMemoryType.PREFERENCE, 100)).thenReturn(List.of(memory, duplicate));

        assertThatThrownBy(() -> service.update(7L, 99L, "偏好中文回答", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("memory edit requires governance");
        verify(memoryMapper, never()).updateContentAndPriority(any(), any(), any(), any(), any());
    }

    @Test
    void shouldKeepActiveMemoryAndIgnoreConflictingSuggestion() {
        UserMemory active = memory(88L, UserMemoryStatus.ACTIVE);
        active.setMemoryType(UserMemoryType.ANSWER_STYLE);
        UserMemory suggested = memory(99L, UserMemoryStatus.SUGGESTED);
        suggested.setMemoryType(UserMemoryType.ANSWER_STYLE);
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(suggested);
        when(memoryMapper.selectByIdAndUserId(7L, 88L)).thenReturn(active);
        when(memoryMapper.updateStatus(7L, 99L, UserMemoryStatus.SUGGESTED, UserMemoryStatus.IGNORED)).thenReturn(1);

        UserMemoryResponse response = service.resolveSuggestion(7L, 99L, "KEEP_ACTIVE", 88L, null, null);

        assertThat(response.status()).isEqualTo(UserMemoryStatus.IGNORED);
        verify(memoryMapper, never()).updateContentAndPriority(any(), any(), any(), any(), any());
    }

    @Test
    void shouldReplaceActiveMemoryWithSuggestion() {
        UserMemory active = memory(88L, UserMemoryStatus.ACTIVE);
        active.setMemoryType(UserMemoryType.ANSWER_STYLE);
        active.setContent("回答保持简洁");
        UserMemory suggested = memory(99L, UserMemoryStatus.SUGGESTED);
        suggested.setMemoryType(UserMemoryType.ANSWER_STYLE);
        suggested.setContent("回答要详细解释");
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(suggested);
        when(memoryMapper.selectByIdAndUserId(7L, 88L)).thenReturn(active);
        when(memoryMapper.selectActiveByUser(7L, UserMemoryType.ANSWER_STYLE, 100)).thenReturn(List.of(active));
        when(memoryMapper.updateContentAndPriority(7L, 88L, UserMemoryStatus.ACTIVE, "回答要详细解释", 40))
                .thenReturn(1);
        when(memoryMapper.updateStatus(7L, 99L, UserMemoryStatus.SUGGESTED, UserMemoryStatus.IGNORED)).thenReturn(1);

        UserMemoryResponse response = service.resolveSuggestion(7L, 99L, "REPLACE_ACTIVE", 88L, null, null);

        assertThat(response.memoryId()).isEqualTo(88L);
        assertThat(response.content()).isEqualTo("回答要详细解释");
        assertThat(suggested.getStatus()).isEqualTo(UserMemoryStatus.IGNORED);
    }

    @Test
    void shouldMergeSuggestionIntoActiveMemoryWithUserContent() {
        UserMemory active = memory(88L, UserMemoryStatus.ACTIVE);
        active.setMemoryType(UserMemoryType.ANSWER_STYLE);
        active.setContent("回答保持简洁");
        UserMemory suggested = memory(99L, UserMemoryStatus.SUGGESTED);
        suggested.setMemoryType(UserMemoryType.ANSWER_STYLE);
        suggested.setContent("先给结论");
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(suggested);
        when(memoryMapper.selectByIdAndUserId(7L, 88L)).thenReturn(active);
        when(memoryMapper.selectActiveByUser(7L, UserMemoryType.ANSWER_STYLE, 100)).thenReturn(List.of(active));
        when(memoryMapper.updateContentAndPriority(7L, 88L, UserMemoryStatus.ACTIVE, "回答保持简洁，并先给结论", 80))
                .thenReturn(1);
        when(memoryMapper.updateStatus(7L, 99L, UserMemoryStatus.SUGGESTED, UserMemoryStatus.IGNORED)).thenReturn(1);

        UserMemoryResponse response = service.resolveSuggestion(
                7L,
                99L,
                "MERGE_WITH_ACTIVE",
                88L,
                "回答保持简洁，并先给结论",
                80
        );

        assertThat(response.content()).isEqualTo("回答保持简洁，并先给结论");
        assertThat(response.priority()).isEqualTo(80);
    }

    @Test
    void shouldRejectResolveWhenMemoryTypesMismatch() {
        UserMemory active = memory(88L, UserMemoryStatus.ACTIVE);
        active.setMemoryType(UserMemoryType.PREFERENCE);
        UserMemory suggested = memory(99L, UserMemoryStatus.SUGGESTED);
        suggested.setMemoryType(UserMemoryType.ANSWER_STYLE);
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(suggested);
        when(memoryMapper.selectByIdAndUserId(7L, 88L)).thenReturn(active);

        assertThatThrownBy(() -> service.resolveSuggestion(7L, 99L, "REPLACE_ACTIVE", 88L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("memory type mismatch");
    }

    @Test
    void shouldRejectAcceptingActiveMemoryAsSuggestion() {
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(memory(99L, UserMemoryStatus.ACTIVE));

        assertThatThrownBy(() -> service.acceptSuggestion(7L, 99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldExposeConflictHintForSuggestedMemory() {
        UserMemory active = memory(88L, UserMemoryStatus.ACTIVE);
        active.setMemoryType(UserMemoryType.ANSWER_STYLE);
        active.setContent("回答保持简洁");
        UserMemory suggested = memory(99L, UserMemoryStatus.SUGGESTED);
        suggested.setMemoryType(UserMemoryType.ANSWER_STYLE);
        suggested.setContent("回答要详细解释");
        when(memoryMapper.selectByUserAndStatus(7L, UserMemoryStatus.SUGGESTED, null, 50))
                .thenReturn(List.of(suggested));
        when(memoryMapper.selectActiveByUser(7L, UserMemoryType.ANSWER_STYLE, 100)).thenReturn(List.of(active));

        List<UserMemoryResponse> responses = service.listSuggestions(7L, null, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).conflictWithId()).isEqualTo(88L);
        assertThat(responses.get(0).governanceHint()).isEqualTo("conflict_active_memory");
    }

    @Test
    void shouldExposeSimilarActiveMemoryHintForSuggestedMemory() {
        UserMemory active = memory(88L, UserMemoryStatus.ACTIVE);
        active.setContent("偏好中文回答A");
        UserMemory suggested = memory(99L, UserMemoryStatus.SUGGESTED);
        suggested.setContent("偏好中文回答");
        when(memoryMapper.selectByUserAndStatus(7L, UserMemoryStatus.SUGGESTED, null, 50))
                .thenReturn(List.of(suggested));
        when(memoryMapper.selectActiveByUser(7L, UserMemoryType.PREFERENCE, 100)).thenReturn(List.of(active));

        List<UserMemoryResponse> responses = service.listSuggestions(7L, null, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).duplicateOfId()).isEqualTo(88L);
        assertThat(responses.get(0).governanceHint()).isEqualTo("similar_active_memory");
        assertThat(responses.get(0).similarityScore()).isGreaterThanOrEqualTo(BigDecimal.valueOf(0.8D));
    }

    @Test
    void shouldRejectAcceptingConflictingSuggestion() {
        UserMemory active = memory(88L, UserMemoryStatus.ACTIVE);
        active.setMemoryType(UserMemoryType.ANSWER_STYLE);
        active.setContent("回答保持简洁");
        UserMemory suggested = memory(99L, UserMemoryStatus.SUGGESTED);
        suggested.setMemoryType(UserMemoryType.ANSWER_STYLE);
        suggested.setContent("回答要详细解释");
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(suggested);
        when(memoryMapper.selectActiveByUser(7L, UserMemoryType.ANSWER_STYLE, 100)).thenReturn(List.of(active));

        assertThatThrownBy(() -> service.acceptSuggestion(7L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("conflict_active_memory");
    }

    @Test
    void shouldRejectCreatingDuplicateActiveMemory() {
        UserMemory active = memory(88L, UserMemoryStatus.ACTIVE);
        when(memoryMapper.selectActiveByUser(7L, UserMemoryType.PREFERENCE, 100)).thenReturn(List.of(active));

        assertThatThrownBy(() -> service.create(7L, UserMemoryType.PREFERENCE, "偏好中文回答", 10, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duplicate active memory");
        verify(memoryMapper, never()).insert(any(UserMemory.class));
    }

    @Test
    void shouldRejectCreatingSimilarActiveMemory() {
        UserMemory active = memory(88L, UserMemoryStatus.ACTIVE);
        active.setContent("偏好中文回答A");
        when(memoryMapper.selectActiveByUser(7L, UserMemoryType.PREFERENCE, 100)).thenReturn(List.of(active));

        assertThatThrownBy(() -> service.create(
                7L,
                UserMemoryType.PREFERENCE,
                "偏好中文回答",
                10,
                null,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("similar_active_memory");
        verify(memoryMapper, never()).insert(any(UserMemory.class));
    }

    @Test
    void shouldAllowSameContentAcrossDifferentMemoryTypes() {
        when(memoryMapper.selectActiveByUser(7L, UserMemoryType.PREFERENCE, 100)).thenReturn(List.of());
        when(memoryMapper.insert(any(UserMemory.class))).thenAnswer(invocation -> {
            UserMemory saved = invocation.getArgument(0);
            saved.setId(100L);
            return 1;
        });

        UserMemoryResponse response = service.create(
                7L,
                UserMemoryType.PREFERENCE,
                "偏好中文回答",
                10,
                null,
                null
        );

        ArgumentCaptor<UserMemory> captor = ArgumentCaptor.forClass(UserMemory.class);
        verify(memoryMapper).insert(captor.capture());
        assertThat(response.status()).isEqualTo(UserMemoryStatus.ACTIVE);
        assertThat(captor.getValue().getMemoryType()).isEqualTo(UserMemoryType.PREFERENCE);
    }

    @Test
    void shouldSoftDeleteActiveMemory() {
        UserMemory memory = memory(99L, UserMemoryStatus.ACTIVE);
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(memory);
        when(memoryMapper.softDeleteByUser(7L, 99L)).thenReturn(1);

        UserMemoryResponse response = service.delete(7L, 99L);

        assertThat(response.memoryId()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(UserMemoryStatus.DELETED);
        verify(memoryMapper).softDeleteByUser(7L, 99L);
    }

    @Test
    void shouldRejectDeletingForeignOrMissingMemory() {
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(7L, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.MEMORY_NOT_FOUND));
        verify(memoryMapper, never()).softDeleteByUser(any(), any());
    }

    @Test
    void shouldRejectDeleteWhenSoftDeleteDoesNotUpdateRow() {
        UserMemory memory = memory(99L, UserMemoryStatus.ACTIVE);
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(memory);
        when(memoryMapper.softDeleteByUser(7L, 99L)).thenReturn(0);

        assertThatThrownBy(() -> service.delete(7L, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR));
    }

    @Test
    void shouldRejectRepeatedDeleteOfAlreadyDeletedMemory() {
        UserMemory memory = memory(99L, UserMemoryStatus.DELETED);
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(memory);
        when(memoryMapper.softDeleteByUser(7L, 99L)).thenReturn(0);

        assertThatThrownBy(() -> service.delete(7L, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR));
    }

    @Test
    void shouldResolveSuggestionOnlyWithinSameUserBoundary() {
        UserMemory suggested = memory(99L, UserMemoryStatus.SUGGESTED);
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(suggested);
        when(memoryMapper.selectByIdAndUserId(7L, 88L)).thenReturn(null);

        assertThatThrownBy(() -> service.resolveSuggestion(7L, 99L, "REPLACE_ACTIVE", 88L, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.MEMORY_NOT_FOUND));
    }

    @Test
    void shouldRejectCreatingSensitiveManualMemory() {
        String sensitive = "请记住 api_key=example-token";
        doThrow(new BusinessException(ErrorCode.MEMORY_SENSITIVE_CONTENT_REJECTED))
                .when(safetyValidator)
                .validate(sensitive);

        assertThatThrownBy(() -> service.create(7L, UserMemoryType.PREFERENCE, sensitive, 10, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.MEMORY_SENSITIVE_CONTENT_REJECTED));
        verify(memoryMapper, never()).insert(any(UserMemory.class));
    }

    @Test
    void shouldRejectResolvingSuggestionIntoSensitiveContent() {
        UserMemory active = memory(88L, UserMemoryStatus.ACTIVE);
        active.setMemoryType(UserMemoryType.ANSWER_STYLE);
        UserMemory suggested = memory(99L, UserMemoryStatus.SUGGESTED);
        suggested.setMemoryType(UserMemoryType.ANSWER_STYLE);
        String mergedContent = "回答保持简洁，并记住 token=example-token";
        when(memoryMapper.selectByIdAndUserId(7L, 99L)).thenReturn(suggested);
        when(memoryMapper.selectByIdAndUserId(7L, 88L)).thenReturn(active);
        doThrow(new BusinessException(ErrorCode.MEMORY_SENSITIVE_CONTENT_REJECTED))
                .when(safetyValidator)
                .validate(mergedContent);

        assertThatThrownBy(() -> service.resolveSuggestion(
                7L,
                99L,
                "MERGE_WITH_ACTIVE",
                88L,
                mergedContent,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> assertThat(((BusinessException) throwable).getErrorCode())
                        .isEqualTo(ErrorCode.MEMORY_SENSITIVE_CONTENT_REJECTED));
        verify(memoryMapper, never()).updateContentAndPriority(any(), any(), any(), any(), any());
        verify(memoryMapper, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void shouldDropSensitiveExtractedSuggestionWithoutInsert() {
        String sensitive = "请记住 api_key=example-token";
        when(extractionService.extractSuggestions(7L, 10L, null)).thenReturn(List.of(
                new MemorySuggestionCandidate(UserMemoryType.PREFERENCE, sensitive, 10L, 101L, 40, 0.7)
        ));
        doThrow(new BusinessException(ErrorCode.MEMORY_SENSITIVE_CONTENT_REJECTED))
                .when(safetyValidator)
                .validate(sensitive);

        List<UserMemoryResponse> responses = service.extractSuggestions(7L, 10L, null);

        assertThat(responses).isEmpty();
        verify(memoryMapper, never()).insert(any(UserMemory.class));
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
