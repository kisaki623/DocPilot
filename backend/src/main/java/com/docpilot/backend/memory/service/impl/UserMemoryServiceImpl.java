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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class UserMemoryServiceImpl implements UserMemoryService {

    private static final int DEFAULT_MEMORY_LIMIT = 50;
    private static final int MAX_MEMORY_LIMIT = 100;
    private static final int CONTENT_MAX_CHARS = 1_000;
    private static final double SIMILARITY_DUPLICATE_THRESHOLD = 0.80D;
    private static final long MEMORY_GOVERNANCE_LOCK_WAIT_SECONDS = 3L;

    private final UserMemoryMapper userMemoryMapper;
    private final MemorySafetyValidator memorySafetyValidator;
    private final MemoryExtractionService memoryExtractionService;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    private enum ResolveAction {
        KEEP_ACTIVE,
        REPLACE_ACTIVE,
        MERGE_WITH_ACTIVE
    }

    public UserMemoryServiceImpl(UserMemoryMapper userMemoryMapper,
                                 MemorySafetyValidator memorySafetyValidator,
                                 MemoryExtractionService memoryExtractionService) {
        this.userMemoryMapper = userMemoryMapper;
        this.memorySafetyValidator = memorySafetyValidator;
        this.memoryExtractionService = memoryExtractionService;
    }

    @Override
    @Transactional
    public UserMemoryResponse create(Long userId,
                                     String memoryType,
                                     String content,
                                     Integer priority,
                                     Long sourceConversationId,
                                     Long sourceMessageId) {
        ValidationUtils.requireNonNull(userId, "userId");
        String resolvedType = UserMemoryType.normalizeOrDefault(memoryType);
        String normalizedContent = normalizeAndValidateContent(content);
        return withActiveMemoryGovernanceLock(userId, resolvedType, () -> {
            ensureNoBlockingGovernance(userId, resolvedType, normalizedContent, null, "duplicate active memory already exists");

            UserMemory memory = new UserMemory();
            memory.setUserId(userId);
            memory.setMemoryType(resolvedType);
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
        });
    }

    @Override
    public List<UserMemoryResponse> list(Long userId, String memoryType, Integer limit) {
        ValidationUtils.requireNonNull(userId, "userId");
        String normalizedType = memoryType == null || memoryType.isBlank()
                ? null
                : UserMemoryType.normalizeOrDefault(memoryType);
        return userMemoryMapper.selectActiveByUser(userId, normalizedType, resolveLimit(limit))
                .stream()
                .map(memory -> UserMemoryResponse.from(memory, duplicateActiveId(memory), null,
                        duplicateActiveId(memory) == null ? "" : "duplicate_active_memory", null))
                .toList();
    }

    @Override
    public List<UserMemoryResponse> listDisabled(Long userId, String memoryType, Integer limit) {
        ValidationUtils.requireNonNull(userId, "userId");
        String normalizedType = memoryType == null || memoryType.isBlank()
                ? null
                : UserMemoryType.normalizeOrDefault(memoryType);
        return userMemoryMapper.selectByUserAndStatus(
                        userId,
                        UserMemoryStatus.ARCHIVED,
                        normalizedType,
                        resolveLimit(limit)
                )
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
                .map(memory -> toResponse(memory, governanceFor(userId, memory)))
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
                .map(memory -> toResponse(memory, governanceFor(userId, memory)))
                .toList();
    }

    @Override
    @Transactional
    public UserMemoryResponse acceptSuggestion(Long userId, Long memoryId) {
        UserMemory memory = requireMemory(userId, memoryId);
        return withActiveMemoryGovernanceLock(userId, memory.getMemoryType(), () -> {
            UserMemory current = requireMemory(userId, memoryId);
            if (!UserMemoryStatus.SUGGESTED.equals(current.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "memory is not a suggestion");
            }
            MemoryGovernance governance = governanceFor(userId, current);
            if (governance.hasBlockingIssue()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "memory suggestion requires governance before accept: " + governance.hint());
            }
            if (userMemoryMapper.updateStatus(userId, memoryId, UserMemoryStatus.SUGGESTED, UserMemoryStatus.ACTIVE) <= 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to accept memory suggestion");
            }
            current.setStatus(UserMemoryStatus.ACTIVE);
            return UserMemoryResponse.from(current);
        });
    }

    @Override
    @Transactional
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
    @Transactional
    public UserMemoryResponse resolveSuggestion(Long userId,
                                                Long memoryId,
                                                String action,
                                                Long activeMemoryId,
                                                String mergedContent,
                                                Integer priority) {
        UserMemory suggestion = requireMemory(userId, memoryId);
        if (!UserMemoryStatus.SUGGESTED.equals(suggestion.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "memory is not a suggestion");
        }
        ValidationUtils.requireNonNull(activeMemoryId, "activeMemoryId");
        UserMemory active = requireMemory(userId, activeMemoryId);
        return withActiveMemoryGovernanceLock(userId, suggestion.getMemoryType(), () -> {
            UserMemory currentSuggestion = requireMemory(userId, memoryId);
            if (!UserMemoryStatus.SUGGESTED.equals(currentSuggestion.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "memory is not a suggestion");
            }
            UserMemory currentActive = requireMemory(userId, activeMemoryId);
            if (!UserMemoryStatus.ACTIVE.equals(currentActive.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "target memory is not active");
            }
            if (!currentActive.getMemoryType().equals(currentSuggestion.getMemoryType())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "memory type mismatch");
            }

            ResolveAction resolvedAction = resolveAction(action);
            if (resolvedAction == ResolveAction.KEEP_ACTIVE) {
                markSuggestionIgnored(userId, memoryId, currentSuggestion);
                return UserMemoryResponse.from(currentSuggestion);
            }

            String nextContent = resolvedAction == ResolveAction.REPLACE_ACTIVE
                    ? currentSuggestion.getContent()
                    : normalizeAndValidateContent(mergedContent);
            if (resolvedAction == ResolveAction.REPLACE_ACTIVE) {
                nextContent = normalizeAndValidateContent(nextContent);
            }
            Integer nextPriority = priority == null ? currentActive.getPriority() : Math.max(0, priority);
            ensureNoBlockingGovernance(userId, currentActive.getMemoryType(), nextContent, activeMemoryId,
                    "memory resolve requires governance");

            if (userMemoryMapper.updateContentAndPriority(
                    userId,
                    activeMemoryId,
                    UserMemoryStatus.ACTIVE,
                    nextContent,
                    nextPriority
            ) <= 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to update active memory");
            }
            currentActive.setContent(nextContent);
            currentActive.setPriority(nextPriority);
            markSuggestionIgnored(userId, memoryId, currentSuggestion);
            return toResponse(currentActive, governanceFor(userId, currentActive));
        });
    }

    @Override
    @Transactional
    public UserMemoryResponse update(Long userId, Long memoryId, String content, Integer priority) {
        UserMemory memory = requireMemory(userId, memoryId);
        String normalizedContent = normalizeAndValidateContent(content);
        return withActiveMemoryGovernanceLock(userId, memory.getMemoryType(), () -> {
            UserMemory current = requireMemory(userId, memoryId);
            if (!UserMemoryStatus.ACTIVE.equals(current.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "only active memory can be edited");
            }
            Integer nextPriority = priority == null ? current.getPriority() : Math.max(0, priority);
            ensureNoBlockingGovernance(userId, current.getMemoryType(), normalizedContent, memoryId,
                    "memory edit requires governance");
            if (userMemoryMapper.updateContentAndPriority(
                    userId,
                    memoryId,
                    UserMemoryStatus.ACTIVE,
                    normalizedContent,
                    nextPriority
            ) <= 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to update user memory");
            }
            current.setContent(normalizedContent);
            current.setPriority(nextPriority);
            return toResponse(current, governanceFor(userId, current));
        });
    }

    @Override
    @Transactional
    public UserMemoryResponse disable(Long userId, Long memoryId) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(memoryId, "memoryId");
        UserMemory memory = requireMemory(userId, memoryId);
        return withActiveMemoryGovernanceLock(userId, memory.getMemoryType(), () -> {
            UserMemory current = requireMemory(userId, memoryId);
            if (UserMemoryStatus.ARCHIVED.equals(current.getStatus())) {
                return UserMemoryResponse.from(current);
            }
            if (!UserMemoryStatus.ACTIVE.equals(current.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "only active memory can be disabled");
            }
            if (userMemoryMapper.updateStatus(userId, memoryId, UserMemoryStatus.ACTIVE, UserMemoryStatus.ARCHIVED) <= 0) {
                UserMemory latest = requireMemory(userId, memoryId);
                if (UserMemoryStatus.ARCHIVED.equals(latest.getStatus())) {
                    return UserMemoryResponse.from(latest);
                }
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to disable user memory");
            }
            current.setStatus(UserMemoryStatus.ARCHIVED);
            return UserMemoryResponse.from(current);
        });
    }

    @Override
    @Transactional
    public UserMemoryResponse restore(Long userId, Long memoryId) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(memoryId, "memoryId");
        UserMemory memory = requireMemory(userId, memoryId);
        return withActiveMemoryGovernanceLock(userId, memory.getMemoryType(), () -> {
            UserMemory current = requireMemory(userId, memoryId);
            if (UserMemoryStatus.ACTIVE.equals(current.getStatus())) {
                return toResponse(current, governanceFor(userId, current));
            }
            if (!UserMemoryStatus.ARCHIVED.equals(current.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "only disabled memory can be restored");
            }
            memorySafetyValidator.validate(current.getContent());
            ensureNoBlockingGovernance(userId, current.getMemoryType(), current.getContent(), memoryId,
                    "memory restore requires governance");
            if (userMemoryMapper.updateStatus(userId, memoryId, UserMemoryStatus.ARCHIVED, UserMemoryStatus.ACTIVE) <= 0) {
                UserMemory latest = requireMemory(userId, memoryId);
                if (UserMemoryStatus.ACTIVE.equals(latest.getStatus())) {
                    return toResponse(latest, governanceFor(userId, latest));
                }
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to restore user memory");
            }
            current.setStatus(UserMemoryStatus.ACTIVE);
            return toResponse(current, governanceFor(userId, current));
        });
    }

    @Override
    @Transactional
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

    private String normalizeAndValidateContent(String content) {
        ValidationUtils.requireNonBlank(content, "content");
        String normalizedContent = content.trim();
        if (normalizedContent.length() > CONTENT_MAX_CHARS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "memory content is too long");
        }
        memorySafetyValidator.validate(normalizedContent);
        return normalizedContent;
    }

    private void ensureNoBlockingGovernance(Long userId,
                                            String memoryType,
                                            String content,
                                            Long excludeMemoryId,
                                            String message) {
        MemoryGovernance governance = governanceFor(userId, memoryType, content, excludeMemoryId);
        if (governance.hasBlockingIssue()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message + ": " + governance.hint());
        }
    }

    private UserMemoryResponse withActiveMemoryGovernanceLock(Long userId,
                                                              String memoryType,
                                                              Supplier<UserMemoryResponse> supplier) {
        if (redissonClient == null) {
            return supplier.get();
        }
        String lockKey = "docpilot:memory:governance:" + userId + ":" + UserMemoryType.normalizeOrDefault(memoryType);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLock(MEMORY_GOVERNANCE_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "memory governance lock interrupted");
        }
        if (!locked) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "memory operation is busy, please retry later");
        }
        try {
            return supplier.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private ResolveAction resolveAction(String action) {
        if (action == null || action.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "resolve action is required");
        }
        try {
            return ResolveAction.valueOf(action.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "resolve action is invalid");
        }
    }

    private void markSuggestionIgnored(Long userId, Long memoryId, UserMemory suggestion) {
        if (userMemoryMapper.updateStatus(userId, memoryId, UserMemoryStatus.SUGGESTED, UserMemoryStatus.IGNORED) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to resolve memory suggestion");
        }
        suggestion.setStatus(UserMemoryStatus.IGNORED);
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

    private UserMemoryResponse toResponse(UserMemory memory, MemoryGovernance governance) {
        MemoryGovernance resolved = governance == null ? MemoryGovernance.none() : governance;
        return UserMemoryResponse.from(
                memory,
                resolved.duplicateOfId(),
                resolved.conflictWithId(),
                resolved.hint(),
                resolved.similarityScore()
        );
    }

    private UserMemory findDuplicateActive(Long userId, String memoryType, String content, Long excludeMemoryId) {
        MemoryGovernance governance = governanceFor(userId, memoryType, content, excludeMemoryId);
        if ("duplicate_active_memory".equals(governance.hint())
                || "similar_active_memory".equals(governance.hint())) {
            Long duplicateId = governance.duplicateOfId();
            if (duplicateId == null) {
                return null;
            }
            return activeMemories(userId, memoryType).stream()
                    .filter(memory -> duplicateId.equals(memory.getId()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private Long duplicateActiveId(UserMemory memory) {
        if (memory == null || !UserMemoryStatus.ACTIVE.equals(memory.getStatus())) {
            return null;
        }
        return activeMemories(memory.getUserId(), memory.getMemoryType()).stream()
                .filter(candidate -> !candidate.getId().equals(memory.getId()))
                .filter(candidate -> normalize(candidate.getContent()).equals(normalize(memory.getContent())))
                .map(UserMemory::getId)
                .findFirst()
                .orElse(null);
    }

    private MemoryGovernance governanceFor(Long userId, UserMemory memory) {
        if (memory == null) {
            return MemoryGovernance.none();
        }
        return governanceFor(userId, memory.getMemoryType(), memory.getContent(), memory.getId());
    }

    private MemoryGovernance governanceFor(Long userId, String memoryType, String content, Long excludeMemoryId) {
        String normalizedContent = normalize(content);
        if (normalizedContent.isBlank()) {
            return MemoryGovernance.none();
        }
        for (UserMemory active : activeMemories(userId, memoryType)) {
            if (excludeMemoryId != null && excludeMemoryId.equals(active.getId())) {
                continue;
            }
            String normalizedActive = normalize(active.getContent());
            if (normalizedContent.equals(normalizedActive)) {
                return new MemoryGovernance(active.getId(), null, "duplicate_active_memory", BigDecimal.ONE);
            }
            double similarity = jaccardSimilarity(normalizedContent, normalizedActive);
            if (similarity >= SIMILARITY_DUPLICATE_THRESHOLD) {
                return new MemoryGovernance(active.getId(), null, "similar_active_memory", decimal(similarity));
            }
            if (conflicts(normalizedContent, normalizedActive)) {
                return new MemoryGovernance(null, active.getId(), "conflict_active_memory", decimal(similarity));
            }
        }
        return MemoryGovernance.none();
    }

    private List<UserMemory> activeMemories(Long userId, String memoryType) {
        if (userId == null) {
            return List.of();
        }
        List<UserMemory> memories = userMemoryMapper.selectActiveByUser(userId, memoryType, MAX_MEMORY_LIMIT);
        return memories == null ? List.of() : memories;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s]+", "")
                .trim();
    }

    private double jaccardSimilarity(String left, String right) {
        Set<String> leftTerms = terms(left);
        Set<String> rightTerms = terms(right);
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) {
            return 0.0D;
        }
        Set<String> intersection = new LinkedHashSet<>(leftTerms);
        intersection.retainAll(rightTerms);
        Set<String> union = new LinkedHashSet<>(leftTerms);
        union.addAll(rightTerms);
        return (double) intersection.size() / union.size();
    }

    private Set<String> terms(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> terms = new LinkedHashSet<>(Arrays.asList(value.split("[\\p{Punct}\\s]+")));
        if (terms.size() <= 1 && value.length() >= 2) {
            terms.clear();
            for (int i = 0; i < value.length() - 1; i++) {
                terms.add(value.substring(i, i + 2));
            }
        }
        terms.remove("");
        return terms;
    }

    private boolean conflicts(String left, String right) {
        return opposite(left, right, List.of("简洁", "简短", "concise", "brief"),
                List.of("详细", "detailed", "verbose"))
                || opposite(left, right, List.of("中文", "chinese"),
                List.of("英文", "english"))
                || opposite(left, right, List.of("先给结论", "结论先行", "conclusionfirst"),
                List.of("先解释", "背景优先", "backgroundfirst"));
    }

    private boolean opposite(String left, String right, List<String> first, List<String> second) {
        return (containsAny(left, first) && containsAny(right, second))
                || (containsAny(left, second) && containsAny(right, first));
    }

    private boolean containsAny(String text, List<String> values) {
        return values.stream().anyMatch(text::contains);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_MEMORY_LIMIT;
        }
        return Math.min(limit, MAX_MEMORY_LIMIT);
    }

    private record MemoryGovernance(
            Long duplicateOfId,
            Long conflictWithId,
            String hint,
            BigDecimal similarityScore
    ) {
        static MemoryGovernance none() {
            return new MemoryGovernance(null, null, "", null);
        }

        boolean hasBlockingIssue() {
            return duplicateOfId != null || conflictWithId != null;
        }
    }
}
