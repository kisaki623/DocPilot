package com.docpilot.backend.memory.service.impl;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.memory.constant.UserMemorySourceType;
import com.docpilot.backend.memory.constant.UserMemoryStatus;
import com.docpilot.backend.memory.constant.UserMemoryType;
import com.docpilot.backend.memory.entity.UserMemory;
import com.docpilot.backend.memory.mapper.UserMemoryMapper;
import com.docpilot.backend.memory.service.MemoryExtractionService;
import com.docpilot.backend.memory.service.MemorySafetyValidator;
import com.docpilot.backend.memory.service.MemorySuggestionCandidate;
import com.docpilot.backend.memory.service.UserMemoryService;
import com.docpilot.backend.memory.vo.UserMemoryResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class UserMemoryServiceImpl implements UserMemoryService {

    private static final int DEFAULT_MEMORY_LIMIT = 50;
    private static final int MAX_MEMORY_LIMIT = 100;
    private static final int CONTENT_MAX_CHARS = 1_000;

    private final UserMemoryMapper userMemoryMapper;
    private final MemorySafetyValidator memorySafetyValidator;
    private final MemoryExtractionService memoryExtractionService;

    public UserMemoryServiceImpl(UserMemoryMapper userMemoryMapper,
                                 MemorySafetyValidator memorySafetyValidator,
                                 MemoryExtractionService memoryExtractionService) {
        this.userMemoryMapper = userMemoryMapper;
        this.memorySafetyValidator = memorySafetyValidator;
        this.memoryExtractionService = memoryExtractionService;
    }

    @Override
    public UserMemoryResponse create(Long userId,
                                     String memoryType,
                                     String content,
                                     Integer priority,
                                     Long sourceConversationId,
                                     Long sourceMessageId) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonBlank(content, "content");
        String normalizedContent = content.trim();
        if (normalizedContent.length() > CONTENT_MAX_CHARS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "memory content is too long");
        }
        memorySafetyValidator.validate(normalizedContent);

        UserMemory memory = new UserMemory();
        memory.setUserId(userId);
        memory.setMemoryType(UserMemoryType.normalizeOrDefault(memoryType));
        memory.setContent(normalizedContent);
        memory.setSourceType(UserMemorySourceType.MANUAL);
        memory.setSourceConversationId(sourceConversationId);
        memory.setSourceMessageId(sourceMessageId);
        memory.setStatus(UserMemoryStatus.ACTIVE);
        memory.setPriority(priority == null ? 0 : Math.max(0, priority));
        memory.setConfidence(BigDecimal.ONE);
        memory.setUseCount(0);

        if (userMemoryMapper.insert(memory) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to save user memory");
        }
        return UserMemoryResponse.from(memory);
    }

    @Override
    public List<UserMemoryResponse> list(Long userId, String memoryType, Integer limit) {
        ValidationUtils.requireNonNull(userId, "userId");
        String normalizedType = memoryType == null || memoryType.isBlank()
                ? null
                : UserMemoryType.normalizeOrDefault(memoryType);
        return userMemoryMapper.selectActiveByUser(userId, normalizedType, resolveLimit(limit))
                .stream()
                .map(UserMemoryResponse::from)
                .toList();
    }

    @Override
    public List<UserMemoryResponse> listSuggestions(Long userId, String memoryType, Integer limit) {
        ValidationUtils.requireNonNull(userId, "userId");
        String normalizedType = memoryType == null || memoryType.isBlank()
                ? null
                : UserMemoryType.normalizeOrDefault(memoryType);
        return userMemoryMapper.selectByUserAndStatus(
                        userId,
                        UserMemoryStatus.SUGGESTED,
                        normalizedType,
                        resolveLimit(limit)
                )
                .stream()
                .map(UserMemoryResponse::from)
                .toList();
    }

    @Override
    public List<UserMemoryResponse> extractSuggestions(Long userId, Long conversationId, Integer limit) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(conversationId, "conversationId");
        return memoryExtractionService.extractSuggestions(userId, conversationId, limit)
                .stream()
                .map(candidate -> saveSuggestion(userId, candidate))
                .filter(memory -> UserMemoryStatus.SUGGESTED.equals(memory.getStatus()))
                .map(UserMemoryResponse::from)
                .toList();
    }

    @Override
    public UserMemoryResponse acceptSuggestion(Long userId, Long memoryId) {
        UserMemory memory = requireMemory(userId, memoryId);
        if (!UserMemoryStatus.SUGGESTED.equals(memory.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "memory is not a suggestion");
        }
        if (userMemoryMapper.updateStatus(userId, memoryId, UserMemoryStatus.SUGGESTED, UserMemoryStatus.ACTIVE) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to accept memory suggestion");
        }
        memory.setStatus(UserMemoryStatus.ACTIVE);
        return UserMemoryResponse.from(memory);
    }

    @Override
    public UserMemoryResponse ignoreSuggestion(Long userId, Long memoryId) {
        UserMemory memory = requireMemory(userId, memoryId);
        if (!UserMemoryStatus.SUGGESTED.equals(memory.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "memory is not a suggestion");
        }
        if (userMemoryMapper.updateStatus(userId, memoryId, UserMemoryStatus.SUGGESTED, UserMemoryStatus.IGNORED) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to ignore memory suggestion");
        }
        memory.setStatus(UserMemoryStatus.IGNORED);
        return UserMemoryResponse.from(memory);
    }

    @Override
    public UserMemoryResponse delete(Long userId, Long memoryId) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(memoryId, "memoryId");
        UserMemory memory = requireMemory(userId, memoryId);
        if (userMemoryMapper.softDeleteByUser(userId, memoryId) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to delete user memory");
        }
        memory.setStatus(UserMemoryStatus.DELETED);
        return UserMemoryResponse.from(memory);
    }

    private UserMemory saveSuggestion(Long userId, MemorySuggestionCandidate candidate) {
        String memoryType = UserMemoryType.normalizeOrDefault(candidate.memoryType());
        String content = candidate.content() == null ? "" : candidate.content().trim();
        if (content.isBlank() || content.length() > CONTENT_MAX_CHARS) {
            return inactiveSuggestion();
        }
        try {
            memorySafetyValidator.validate(content);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.MEMORY_SENSITIVE_CONTENT_REJECTED) {
                return inactiveSuggestion();
            }
            throw ex;
        }

        UserMemory existing = userMemoryMapper.selectExistingCandidate(userId, memoryType, content);
        if (existing != null) {
            return existing;
        }

        UserMemory memory = new UserMemory();
        memory.setUserId(userId);
        memory.setMemoryType(memoryType);
        memory.setContent(content);
        memory.setSourceType(UserMemorySourceType.SYSTEM_EXTRACTED);
        memory.setSourceConversationId(candidate.sourceConversationId());
        memory.setSourceMessageId(candidate.sourceMessageId());
        memory.setStatus(UserMemoryStatus.SUGGESTED);
        memory.setPriority(Math.max(0, candidate.priority()));
        memory.setConfidence(BigDecimal.valueOf(candidate.confidence()));
        memory.setUseCount(0);

        if (userMemoryMapper.insert(memory) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to save memory suggestion");
        }
        return memory;
    }

    private UserMemory requireMemory(Long userId, Long memoryId) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(memoryId, "memoryId");
        UserMemory memory = userMemoryMapper.selectByIdAndUserId(userId, memoryId);
        if (memory == null) {
            throw new BusinessException(ErrorCode.MEMORY_NOT_FOUND);
        }
        return memory;
    }

    private UserMemory inactiveSuggestion() {
        UserMemory memory = new UserMemory();
        memory.setStatus(UserMemoryStatus.IGNORED);
        return memory;
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_MEMORY_LIMIT;
        }
        return Math.min(limit, MAX_MEMORY_LIMIT);
    }
}
