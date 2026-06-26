package com.docpilot.backend.conversation.service;

import com.docpilot.backend.ai.context.ContextTrace;

public interface ConversationContextTraceService {

    void save(Long userId, ContextTrace trace);

    ContextTrace getByMessage(Long userId, Long conversationId, Long messageId);
}
