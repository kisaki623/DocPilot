package com.docpilot.backend.conversation.service;

import com.docpilot.backend.conversation.entity.ConversationSummary;
import com.docpilot.backend.conversation.vo.ConversationSummaryResponse;

public interface ConversationSummaryService {

    ConversationSummary getActiveSummary(Long userId, Long conversationId);

    ConversationSummaryResponse getSummary(Long userId, Long conversationId);

    ConversationSummaryResponse refreshSummary(Long userId, Long conversationId);

    ConversationSummaryResponse deleteSummary(Long userId, Long conversationId);
}
