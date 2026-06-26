package com.docpilot.backend.conversation.service;

import com.docpilot.backend.conversation.vo.ConversationMessageResponse;
import com.docpilot.backend.ai.context.ContextTrace;

import java.util.List;

public interface ConversationMessageService {

    ConversationMessageResponse send(Long userId, Long conversationId, String content);

    List<ConversationMessageResponse> list(Long userId, Long conversationId, Integer limit);

    ContextTrace getTrace(Long userId, Long conversationId, Long messageId);
}
