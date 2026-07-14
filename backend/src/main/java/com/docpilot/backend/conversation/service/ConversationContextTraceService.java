package com.docpilot.backend.conversation.service;

import com.docpilot.backend.ai.context.ContextTrace;

import java.util.List;
import java.util.Map;

public interface ConversationContextTraceService {

    void save(Long userId, ContextTrace trace);

    Map<Long, ContextTrace> listByMessages(Long userId, Long conversationId, List<Long> messageIds);

    ContextTrace getByMessage(Long userId, Long conversationId, Long messageId);
}
