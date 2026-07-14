package com.docpilot.backend.conversation.service.impl;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.conversation.constant.ConversationContextMode;
import com.docpilot.backend.conversation.constant.ConversationStatus;
import com.docpilot.backend.conversation.entity.Conversation;
import com.docpilot.backend.conversation.mapper.ConversationMapper;
import com.docpilot.backend.conversation.service.ConversationService;
import com.docpilot.backend.conversation.vo.ConversationResponse;
import com.docpilot.backend.knowledge.service.KnowledgeBaseScopeGuard;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConversationServiceImpl implements ConversationService {

    private static final int DEFAULT_CONVERSATION_LIMIT = 50;
    private static final int MAX_CONVERSATION_LIMIT = 100;
    private static final int TITLE_MAX_LENGTH = 128;

    private final ConversationMapper conversationMapper;
    private final KnowledgeBaseScopeGuard knowledgeBaseScopeGuard;

    public ConversationServiceImpl(ConversationMapper conversationMapper,
                                   KnowledgeBaseScopeGuard knowledgeBaseScopeGuard) {
        this.conversationMapper = conversationMapper;
        this.knowledgeBaseScopeGuard = knowledgeBaseScopeGuard;
    }

    @Override
    public ConversationResponse create(Long userId, String title, String contextMode, Long boundKnowledgeBaseId) {
        ValidationUtils.requireNonNull(userId, "userId");
        String resolvedMode = ConversationContextMode.normalizeOrDefault(contextMode);
        if (boundKnowledgeBaseId != null) {
            knowledgeBaseScopeGuard.requireOwnedKnowledgeBase(userId, boundKnowledgeBaseId);
        }

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(resolveTitle(title));
        conversation.setContextMode(resolvedMode);
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setBoundKnowledgeBaseId(boundKnowledgeBaseId);
        conversation.setSummaryEnabled(ConversationContextMode.isAgentMemory(resolvedMode));
        conversation.setMemoryEnabled(ConversationContextMode.isAgentMemory(resolvedMode));
        conversation.setLastMessageTime(null);

        if (conversationMapper.insert(conversation) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to create conversation");
        }
        return ConversationResponse.from(conversation);
    }

    @Override
    public List<ConversationResponse> list(Long userId, Integer limit) {
        ValidationUtils.requireNonNull(userId, "userId");
        return conversationMapper.selectActiveByUserId(userId, resolveLimit(limit))
                .stream()
                .map(ConversationResponse::from)
                .toList();
    }

    @Override
    public ConversationResponse detail(Long userId, Long conversationId) {
        return ConversationResponse.from(requireOwnedActive(userId, conversationId));
    }

    @Override
    public Conversation requireOwnedActive(Long userId, Long conversationId) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(conversationId, "conversationId");
        Conversation conversation = conversationMapper.selectActiveByIdAndUserId(userId, conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    @Override
    public ConversationResponse bindKnowledgeBase(Long userId, Long conversationId, Long knowledgeBaseId) {
        ValidationUtils.requireNonNull(knowledgeBaseId, "knowledgeBaseId");
        Conversation conversation = requireOwnedActive(userId, conversationId);
        knowledgeBaseScopeGuard.requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        if (conversationMapper.updateBoundKnowledgeBase(userId, conversationId, knowledgeBaseId) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to bind knowledge base");
        }
        conversation.setBoundKnowledgeBaseId(knowledgeBaseId);
        return ConversationResponse.from(conversation);
    }

    @Override
    public ConversationResponse unbindKnowledgeBase(Long userId, Long conversationId) {
        Conversation conversation = requireOwnedActive(userId, conversationId);
        if (conversationMapper.updateBoundKnowledgeBase(userId, conversationId, null) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to unbind knowledge base");
        }
        conversation.setBoundKnowledgeBaseId(null);
        return ConversationResponse.from(conversation);
    }

    public void touchLastMessageTime(Long userId, Long conversationId, LocalDateTime lastMessageTime) {
        conversationMapper.updateLastMessageTime(userId, conversationId, lastMessageTime);
    }

    private String resolveTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isBlank()) {
            return "新会话";
        }
        if (normalized.length() <= TITLE_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, TITLE_MAX_LENGTH);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_CONVERSATION_LIMIT;
        }
        return Math.min(limit, MAX_CONVERSATION_LIMIT);
    }
}
