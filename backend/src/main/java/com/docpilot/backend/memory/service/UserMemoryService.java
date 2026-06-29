package com.docpilot.backend.memory.service;

import com.docpilot.backend.memory.vo.UserMemoryResponse;

import java.util.List;

public interface UserMemoryService {

    UserMemoryResponse create(Long userId,
                              String memoryType,
                              String content,
                              Integer priority,
                              Long sourceConversationId,
                              Long sourceMessageId);

    List<UserMemoryResponse> list(Long userId, String memoryType, Integer limit);

    List<UserMemoryResponse> listSuggestions(Long userId, String memoryType, Integer limit);

    List<UserMemoryResponse> extractSuggestions(Long userId, Long conversationId, Integer limit);

    UserMemoryResponse acceptSuggestion(Long userId, Long memoryId);

    UserMemoryResponse ignoreSuggestion(Long userId, Long memoryId);

    UserMemoryResponse resolveSuggestion(Long userId,
                                         Long memoryId,
                                         String action,
                                         Long activeMemoryId,
                                         String mergedContent,
                                         Integer priority);

    UserMemoryResponse update(Long userId, Long memoryId, String content, Integer priority);

    UserMemoryResponse delete(Long userId, Long memoryId);
}
