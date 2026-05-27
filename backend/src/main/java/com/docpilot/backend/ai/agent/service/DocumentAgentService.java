package com.docpilot.backend.ai.agent.service;

import com.docpilot.backend.ai.agent.dto.DocumentAgentRequest;
import com.docpilot.backend.ai.agent.vo.DocumentAgentResponse;

public interface DocumentAgentService {

    DocumentAgentResponse run(Long userId, DocumentAgentRequest request);
}
