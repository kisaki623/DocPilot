package com.docpilot.backend.conversation.service.impl;

import com.docpilot.backend.ai.context.token.TokenEstimator;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.conversation.constant.ConversationSummaryStatus;
import com.docpilot.backend.conversation.entity.ConversationMessage;
import com.docpilot.backend.conversation.entity.ConversationSummary;
import com.docpilot.backend.conversation.mapper.ConversationMessageMapper;
import com.docpilot.backend.conversation.mapper.ConversationSummaryMapper;
import com.docpilot.backend.conversation.service.ConversationService;
import com.docpilot.backend.conversation.service.ConversationSummaryService;
import com.docpilot.backend.conversation.vo.ConversationSummaryResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationSummaryServiceImpl implements ConversationSummaryService {

    private static final int SUMMARY_MESSAGE_LIMIT = 40;
    private static final int SUMMARY_MAX_MESSAGE_CHARS = 240;
    private static final int SUMMARY_MAX_CHARS = 4_000;

    private final ConversationService conversationService;
    private final ConversationSummaryMapper conversationSummaryMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final TokenEstimator tokenEstimator;

    public ConversationSummaryServiceImpl(ConversationService conversationService,
                                          ConversationSummaryMapper conversationSummaryMapper,
                                          ConversationMessageMapper conversationMessageMapper,
                                          TokenEstimator tokenEstimator) {
        this.conversationService = conversationService;
        this.conversationSummaryMapper = conversationSummaryMapper;
        this.conversationMessageMapper = conversationMessageMapper;
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public ConversationSummary getActiveSummary(Long userId, Long conversationId) {
        conversationService.requireOwnedActive(userId, conversationId);
        return conversationSummaryMapper.selectActiveSummary(userId, conversationId);
    }

    @Override
    public ConversationSummaryResponse getSummary(Long userId, Long conversationId) {
        ConversationSummary summary = getActiveSummary(userId, conversationId);
        if (summary == null) {
            return ConversationSummaryResponse.empty(conversationId);
        }
        return ConversationSummaryResponse.from(summary);
    }

    @Override
    public ConversationSummaryResponse refreshSummary(Long userId, Long conversationId) {
        conversationService.requireOwnedActive(userId, conversationId);
        List<ConversationMessage> messages = conversationMessageMapper.selectActiveByConversation(
                userId,
                conversationId,
                SUMMARY_MESSAGE_LIMIT
        );
        if (messages.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "conversation has no messages to summarize");
        }

        ConversationSummary activeSummary = conversationSummaryMapper.selectActiveSummary(userId, conversationId);
        int nextVersion = activeSummary == null || activeSummary.getSummaryVersion() == null
                ? 1
                : activeSummary.getSummaryVersion() + 1;
        conversationSummaryMapper.softDeleteByConversation(userId, conversationId);

        String summaryText = buildExtractiveSummary(messages);
        ConversationSummary summary = new ConversationSummary();
        summary.setConversationId(conversationId);
        summary.setUserId(userId);
        summary.setSummary(summaryText);
        summary.setCoveredStartSeq(messages.get(0).getSequenceNo());
        summary.setCoveredEndSeq(messages.get(messages.size() - 1).getSequenceNo());
        summary.setSummaryVersion(nextVersion);
        summary.setStatus(ConversationSummaryStatus.ACTIVE);
        summary.setTokenCount(tokenEstimator.estimate(summaryText));

        if (conversationSummaryMapper.insert(summary) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to refresh conversation summary");
        }
        return ConversationSummaryResponse.from(summary);
    }

    @Override
    public ConversationSummaryResponse deleteSummary(Long userId, Long conversationId) {
        conversationService.requireOwnedActive(userId, conversationId);
        int updated = conversationSummaryMapper.softDeleteByConversation(userId, conversationId);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.SUMMARY_NOT_FOUND);
        }
        return new ConversationSummaryResponse(conversationId, null, null, null, null,
                "DELETED", 0, null);
    }

    private String buildExtractiveSummary(List<ConversationMessage> messages) {
        StringBuilder builder = new StringBuilder("Extractive conversation summary:\n");
        for (ConversationMessage message : messages) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String role = message.getRole() == null ? "UNKNOWN" : message.getRole();
            String content = compact(message.getContent(), SUMMARY_MAX_MESSAGE_CHARS);
            builder.append("- ")
                    .append(role)
                    .append(" #")
                    .append(message.getSequenceNo() == null ? 0 : message.getSequenceNo())
                    .append(": ")
                    .append(content)
                    .append('\n');
            if (builder.length() >= SUMMARY_MAX_CHARS) {
                break;
            }
        }
        return compact(builder.toString().trim(), SUMMARY_MAX_CHARS);
    }

    private String compact(String text, int maxChars) {
        String compacted = text.replaceAll("\\s+", " ").trim();
        if (compacted.length() <= maxChars) {
            return compacted;
        }
        return compacted.substring(0, Math.max(0, maxChars - 3)) + "...";
    }
}
