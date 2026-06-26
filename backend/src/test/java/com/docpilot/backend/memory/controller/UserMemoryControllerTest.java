package com.docpilot.backend.memory.controller;

import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.memory.dto.MemorySuggestionExtractRequest;
import com.docpilot.backend.memory.dto.UserMemoryCreateRequest;
import com.docpilot.backend.memory.service.UserMemoryService;
import com.docpilot.backend.memory.vo.UserMemoryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserMemoryControllerTest {

    private final UserMemoryService userMemoryService = mock(UserMemoryService.class);
    private final UserMemoryController controller = new UserMemoryController(userMemoryService);

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void shouldCreateWithCurrentUser() {
        UserHolder.setUserId(7L);
        UserMemoryCreateRequest request = new UserMemoryCreateRequest();
        request.setMemoryType("PREFERENCE");
        request.setContent("偏好结论先行");
        request.setPriority(10);
        when(userMemoryService.create(org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any())).thenReturn(response());

        controller.create(request);

        verify(userMemoryService).create(7L, "PREFERENCE", "偏好结论先行", 10, null, null);
    }

    @Test
    void shouldListWithCurrentUser() {
        UserHolder.setUserId(7L);
        when(userMemoryService.list(7L, "PROJECT_STATE", 5)).thenReturn(List.of());

        controller.list("PROJECT_STATE", 5);

        verify(userMemoryService).list(7L, "PROJECT_STATE", 5);
    }

    @Test
    void shouldDeleteWithCurrentUser() {
        UserHolder.setUserId(7L);
        when(userMemoryService.delete(7L, 99L)).thenReturn(response());

        controller.delete(99L);

        verify(userMemoryService).delete(7L, 99L);
    }

    @Test
    void shouldExtractSuggestionsWithCurrentUser() {
        UserHolder.setUserId(7L);
        MemorySuggestionExtractRequest request = new MemorySuggestionExtractRequest();
        request.setConversationId(10L);
        request.setLimit(20);
        when(userMemoryService.extractSuggestions(7L, 10L, 20)).thenReturn(List.of());

        controller.extractSuggestions(request);

        verify(userMemoryService).extractSuggestions(7L, 10L, 20);
    }

    @Test
    void shouldAcceptSuggestionWithCurrentUser() {
        UserHolder.setUserId(7L);
        when(userMemoryService.acceptSuggestion(7L, 99L)).thenReturn(response());

        controller.acceptSuggestion(99L);

        verify(userMemoryService).acceptSuggestion(7L, 99L);
    }

    @Test
    void shouldIgnoreSuggestionWithCurrentUser() {
        UserHolder.setUserId(7L);
        when(userMemoryService.ignoreSuggestion(7L, 99L)).thenReturn(response());

        controller.ignoreSuggestion(99L);

        verify(userMemoryService).ignoreSuggestion(7L, 99L);
    }

    private UserMemoryResponse response() {
        return new UserMemoryResponse(99L, "PREFERENCE", "content", "MANUAL",
                null, null, "ACTIVE", 0, null, null, null);
    }
}
