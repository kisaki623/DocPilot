package com.docpilot.backend.conversation.service.impl;

import com.docpilot.backend.ai.context.ContextAssemblyRequest;
import com.docpilot.backend.ai.context.ContextAssemblyResult;
import com.docpilot.backend.ai.context.ContextTrace;
import com.docpilot.backend.ai.context.token.TokenEstimator;
import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.conversation.constant.ConversationMessageRole;
import com.docpilot.backend.conversation.constant.ConversationMessageStatus;
import com.docpilot.backend.conversation.entity.Conversation;
import com.docpilot.backend.conversation.entity.ConversationMessage;
import com.docpilot.backend.conversation.mapper.ConversationMapper;
import com.docpilot.backend.conversation.mapper.ConversationMessageMapper;
import com.docpilot.backend.conversation.service.ConversationContextTraceService;
import com.docpilot.backend.conversation.service.ConversationMessageService;
import com.docpilot.backend.conversation.service.ConversationService;
import com.docpilot.backend.conversation.vo.ConversationMessageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ConversationMessageServiceImpl implements ConversationMessageService {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_LIMIT = 100;
    private static final int MESSAGE_MAX_CHARS = 8_000;

    private final ConversationService conversationService;
    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final ConversationContextTraceService contextTraceService;
    private final com.docpilot.backend.ai.context.ContextAssemblyService contextAssemblyService;
    private final AiAnswerService aiAnswerService;
    private final TokenEstimator tokenEstimator;
    private final PlatformTransactionManager transactionManager;

    public ConversationMessageServiceImpl(ConversationService conversationService,
                                          ConversationMapper conversationMapper,
                                          ConversationMessageMapper conversationMessageMapper,
                                          ConversationContextTraceService contextTraceService,
                                          com.docpilot.backend.ai.context.ContextAssemblyService contextAssemblyService,
                                          AiAnswerService aiAnswerService,
                                          TokenEstimator tokenEstimator,
                                          PlatformTransactionManager transactionManager) {
        this.conversationService = conversationService;
        this.conversationMapper = conversationMapper;
        this.conversationMessageMapper = conversationMessageMapper;
        this.contextTraceService = contextTraceService;
        this.contextAssemblyService = contextAssemblyService;
        this.aiAnswerService = aiAnswerService;
        this.tokenEstimator = tokenEstimator;
        this.transactionManager = transactionManager;
    }

    @Override
    public ConversationMessageResponse send(Long userId, Long conversationId, String content) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(conversationId, "conversationId");
        ValidationUtils.requireNonBlank(content, "content");
        String currentMessage = content.trim();
        if (currentMessage.length() > MESSAGE_MAX_CHARS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "message content is too long");
        }
        conversationService.requireOwnedActive(userId, conversationId);

        ContextAssemblyResult context = contextAssemblyService.buildContext(new ContextAssemblyRequest(
                userId,
                conversationId,
                currentMessage,
                null
        ));

        String answer = context.modelCallSkipped()
                ? context.fallbackAnswer()
                : aiAnswerService.answer(context.assembledContext(), currentMessage);
        ConversationMessage assistantMessage = saveMessagePairInTransaction(userId, conversationId, currentMessage, answer);
        ContextTrace trace = context.trace().withMessageId(assistantMessage.getId());
        saveTraceBestEffort(userId, trace);
        return ConversationMessageResponse.from(assistantMessage, context.citations(), trace);
    }

    @Override
    public List<ConversationMessageResponse> list(Long userId, Long conversationId, Integer limit) {
        conversationService.requireOwnedActive(userId, conversationId);
        return conversationMessageMapper.selectActiveByConversation(userId, conversationId, resolveLimit(limit))
                .stream()
                .sorted(Comparator.comparingInt(message -> message.getSequenceNo() == null ? 0 : message.getSequenceNo()))
                .map(ConversationMessageResponse::from)
                .toList();
    }

    @Override
    public ContextTrace getTrace(Long userId, Long conversationId, Long messageId) {
        return contextTraceService.getByMessage(userId, conversationId, messageId);
    }

    private ConversationMessage saveMessagePairInTransaction(Long userId,
                                                             Long conversationId,
                                                             String userContent,
                                                             String assistantContent) {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        boolean commitStarted = false;
        try {
            ConversationMessage assistantMessage = saveMessagePair(userId, conversationId, userContent, assistantContent);
            conversationMapper.updateLastMessageTime(userId, conversationId, LocalDateTime.now());
            commitStarted = true;
            transactionManager.commit(status);
            return assistantMessage;
        } catch (RuntimeException ex) {
            if (!commitStarted && !status.isCompleted()) {
                transactionManager.rollback(status);
            }
            throw ex;
        }
    }

    private ConversationMessage saveMessagePair(Long userId, Long conversationId, String userContent, String assistantContent) {
        Conversation lockedConversation = conversationMapper.selectActiveForUpdate(userId, conversationId);
        if (lockedConversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        int nextSequenceNo = conversationMessageMapper.selectMaxSequenceNo(userId, conversationId) + 1;
        insertMessage(userId, conversationId, ConversationMessageRole.USER, userContent, nextSequenceNo);
        return insertMessage(userId, conversationId, ConversationMessageRole.ASSISTANT, assistantContent, nextSequenceNo + 1);
    }

    private ConversationMessage insertMessage(Long userId, Long conversationId, String role, String content, int sequenceNo) {
        ConversationMessage message = new ConversationMessage();
        message.setUserId(userId);
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setSequenceNo(sequenceNo);
        message.setTokenCount(tokenEstimator.estimate(content));
        message.setStatus(ConversationMessageStatus.ACTIVE);

        if (conversationMessageMapper.insert(message) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to save conversation message");
        }
        return message;
    }

    private void saveTraceBestEffort(Long userId, ContextTrace trace) {
        try {
            contextTraceService.save(userId, trace);
        } catch (RuntimeException ignored) {
            // Trace is diagnostic. A trace write failure must not break the user-facing answer path.
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_MESSAGE_LIMIT;
        }
        return Math.min(limit, MAX_MESSAGE_LIMIT);
    }
}
