package com.docpilot.backend.ai.agent.controller;

import com.docpilot.backend.ai.agent.dto.KnowledgeBaseAgentRequest;
import com.docpilot.backend.ai.agent.service.KnowledgeBaseAgentService;
import com.docpilot.backend.ai.agent.vo.KnowledgeBaseAgentResponse;
import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/agent/knowledge-bases")
public class KnowledgeBaseAgentController {

    private final KnowledgeBaseAgentService knowledgeBaseAgentService;

    public KnowledgeBaseAgentController(KnowledgeBaseAgentService knowledgeBaseAgentService) {
        this.knowledgeBaseAgentService = knowledgeBaseAgentService;
    }

    @PostMapping("/{knowledgeBaseId}/run")
    public ApiResponse<KnowledgeBaseAgentResponse> run(@PathVariable Long knowledgeBaseId,
                                                       @RequestBody KnowledgeBaseAgentRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(knowledgeBaseAgentService.run(userId, knowledgeBaseId, request));
    }
}
