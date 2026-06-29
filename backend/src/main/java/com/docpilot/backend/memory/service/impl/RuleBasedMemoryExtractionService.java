package com.docpilot.backend.memory.service.impl;

import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.conversation.constant.ConversationMessageRole;
import com.docpilot.backend.conversation.entity.ConversationMessage;
import com.docpilot.backend.conversation.mapper.ConversationMessageMapper;
import com.docpilot.backend.conversation.service.ConversationService;
import com.docpilot.backend.memory.constant.UserMemoryType;
import com.docpilot.backend.memory.service.MemoryExtractionService;
import com.docpilot.backend.memory.service.MemorySuggestionCandidate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RuleBasedMemoryExtractionService implements MemoryExtractionService {

    private static final int DEFAULT_EXTRACT_LIMIT = 30;
    private static final int MAX_EXTRACT_LIMIT = 80;
    private static final int CANDIDATE_MAX_CHARS = 300;

    private final ConversationService conversationService;
    private final ConversationMessageMapper conversationMessageMapper;

    public RuleBasedMemoryExtractionService(ConversationService conversationService,
                                            ConversationMessageMapper conversationMessageMapper) {
        this.conversationService = conversationService;
        this.conversationMessageMapper = conversationMessageMapper;
    }

    @Override
    public List<MemorySuggestionCandidate> extractSuggestions(Long userId, Long conversationId, Integer limit) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(conversationId, "conversationId");
        conversationService.requireOwnedActive(userId, conversationId);

        return conversationMessageMapper.selectRecentActive(userId, conversationId, resolveLimit(limit))
                .stream()
                .sorted(Comparator.comparingInt(message -> message.getSequenceNo() == null ? 0 : message.getSequenceNo()))
                .map(this::extractOne)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<MemorySuggestionCandidate> extractOne(ConversationMessage message) {
        if (message == null || message.getContent() == null || message.getContent().isBlank()) {
            return Optional.empty();
        }
        if (!ConversationMessageRole.USER.equals(message.getRole())) {
            return Optional.empty();
        }
        String content = compact(message.getContent());
        String normalized = content.toLowerCase(Locale.ROOT);
        if (looksSensitive(normalized) || looksTemporaryInstruction(normalized)) {
            return Optional.empty();
        }

        String memoryType = classify(normalized);
        if (memoryType == null) {
            return Optional.empty();
        }
        int priority = switch (memoryType) {
            case UserMemoryType.ANSWER_STYLE, UserMemoryType.PREFERENCE -> 40;
            case UserMemoryType.TASK_GOAL -> 30;
            case UserMemoryType.PROJECT_STATE, UserMemoryType.TECH_CONTEXT -> 20;
            default -> 10;
        };
        return Optional.of(new MemorySuggestionCandidate(
                memoryType,
                content,
                message.getConversationId(),
                message.getId(),
                priority,
                0.7000
        ));
    }

    private String classify(String normalized) {
        if (containsAny(normalized, "回答", "结论", "简洁", "详细", "中文", "风格", "格式",
                "answer", "conclusion", "concise", "detailed", "style", "format")) {
            return UserMemoryType.ANSWER_STYLE;
        }
        if (containsAny(normalized, "偏好", "喜欢", "习惯", "以后请", "希望你", "不要",
                "prefer", "preference", "habit", "please", "do not")) {
            return UserMemoryType.PREFERENCE;
        }
        if (containsAny(normalized, "目标", "计划", "下一步", "待办", "完成", "阶段",
                "goal", "plan", "next step", "todo", "finish", "phase")) {
            return UserMemoryType.TASK_GOAL;
        }
        if (containsAny(normalized, "docpilot", "项目", "当前任务", "已经实现", "已完成",
                "project", "current task", "implemented", "completed")) {
            return UserMemoryType.PROJECT_STATE;
        }
        if (containsAny(normalized, "spring", "rag", "agent", "qdrant", "rocketmq", "redis", "mysql", "知识库",
                "knowledge base", "retrieval", "evidence")) {
            return UserMemoryType.TECH_CONTEXT;
        }
        return null;
    }

    private boolean looksSensitive(String normalized) {
        return containsAny(normalized,
                "api_key", "apikey", "secret", "password", "passwd", "token", "bearer ",
                "authorization:", "jdbc:", ".env", "ssh-rsa", "private key", "access_key", "accesskey");
    }

    private boolean looksTemporaryInstruction(String normalized) {
        return containsAny(normalized,
                "这一次", "这次", "本次", "临时", "不用记住", "不要记住", "别记住",
                "only this time", "for this answer", "for this response", "do not remember",
                "don't remember", "temporary");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String compact(String value) {
        String compacted = value.replaceAll("\\s+", " ").trim();
        if (compacted.length() <= CANDIDATE_MAX_CHARS) {
            return compacted;
        }
        return compacted.substring(0, CANDIDATE_MAX_CHARS - 3) + "...";
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_EXTRACT_LIMIT;
        }
        return Math.min(limit, MAX_EXTRACT_LIMIT);
    }
}
