package com.docpilot.backend.conversation.service;

import com.docpilot.backend.conversation.entity.Conversation;
import com.docpilot.backend.conversation.vo.ConversationResponse;

import java.util.List;

public interface ConversationService {

    ConversationResponse create(Long userId, String title, String contextMode, Long boundKnowledgeBaseId);

    List<ConversationResponse> list(Long userId, Integer limit);

    ConversationResponse detail(Long userId, Long conversationId);

    Conversation requireOwnedActive(Long userId, Long conversationId);

    ConversationResponse bindKnowledgeBase(Long userId, Long conversationId, Long knowledgeBaseId);

    ConversationResponse unbindKnowledgeBase(Long userId, Long conversationId);
}
