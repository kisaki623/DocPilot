package com.docpilot.backend.ai.agent.service;

import com.docpilot.backend.ai.agent.dto.KnowledgeBaseAgentRequest;
import com.docpilot.backend.ai.agent.vo.KnowledgeBaseAgentResponse;

public interface KnowledgeBaseAgentService {

    KnowledgeBaseAgentResponse run(Long userId, Long knowledgeBaseId, KnowledgeBaseAgentRequest request);
}
