package com.docpilot.backend.ai.agent.service;

import com.docpilot.backend.ai.agent.dto.ToolCallRequest;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
import com.docpilot.backend.ai.agent.vo.ToolSpecResponse;

import java.util.List;

public interface ToolCallService {

    List<ToolSpecResponse> listTools();

    ToolCallResult call(Long currentUserId, ToolCallRequest request);
}
