package com.docpilot.backend.ai.agent.controller;

import com.docpilot.backend.ai.agent.dto.DocumentAgentRequest;
import com.docpilot.backend.ai.agent.service.DocumentAgentService;
import com.docpilot.backend.ai.agent.vo.DocumentAgentResponse;
import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/agent")
public class DocumentAgentController {

    private final DocumentAgentService documentAgentService;

    public DocumentAgentController(DocumentAgentService documentAgentService) {
        this.documentAgentService = documentAgentService;
    }

    @PostMapping("/run")
    public ApiResponse<DocumentAgentResponse> run(@RequestBody DocumentAgentRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(documentAgentService.run(userId, request));
    }
}
