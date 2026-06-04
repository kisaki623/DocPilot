package com.docpilot.backend.ai.agent.controller;

import com.docpilot.backend.ai.agent.dto.ToolCallRequest;
import com.docpilot.backend.ai.agent.service.ToolCallService;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
import com.docpilot.backend.ai.agent.vo.ToolSpecResponse;
import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/tools")
public class AgentToolController {

    private final ToolCallService toolCallService;

    public AgentToolController(ToolCallService toolCallService) {
        this.toolCallService = toolCallService;
    }

    @GetMapping
    public ApiResponse<List<ToolSpecResponse>> listTools() {
        return ApiResponse.success(toolCallService.listTools());
    }

    @PostMapping("/call")
    public ApiResponse<ToolCallResult> call(@RequestBody ToolCallRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(toolCallService.call(userId, request));
    }
}
