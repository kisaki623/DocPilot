package com.docpilot.backend.ai.agent.service.impl;

import com.docpilot.backend.ai.agent.dto.ToolCallRequest;
import com.docpilot.backend.ai.agent.service.ToolCallService;
import com.docpilot.backend.ai.agent.tool.AgentTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentSearchTool;
import com.docpilot.backend.ai.agent.tool.DocumentStatusTool;
import com.docpilot.backend.ai.agent.tool.KnowledgeBaseSearchTool;
import com.docpilot.backend.ai.agent.tool.ToolRegistry;
import com.docpilot.backend.ai.agent.tool.spec.ToolArgumentValidator;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
import com.docpilot.backend.ai.agent.tool.spec.ToolInputMapper;
import com.docpilot.backend.ai.agent.tool.spec.ToolSpec;
import com.docpilot.backend.ai.agent.tool.spec.ToolSpecRegistry;
import com.docpilot.backend.ai.agent.vo.ToolSpecResponse;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ToolCallServiceImpl implements ToolCallService {

    private static final Set<String> CALLABLE_TOOLS = Set.of(
            "document_status_tool",
            DocumentSearchTool.TOOL_NAME,
            KnowledgeBaseSearchTool.TOOL_NAME,
            DocumentRagQaTool.TOOL_NAME
    );

    private final ToolSpecRegistry toolSpecRegistry;
    private final ToolRegistry toolRegistry;
    private final ToolArgumentValidator argumentValidator;
    private final ToolInputMapper inputMapper;

    public ToolCallServiceImpl(ToolSpecRegistry toolSpecRegistry,
                               ToolRegistry toolRegistry,
                               ToolArgumentValidator argumentValidator,
                               ToolInputMapper inputMapper) {
        this.toolSpecRegistry = toolSpecRegistry;
        this.toolRegistry = toolRegistry;
        this.argumentValidator = argumentValidator;
        this.inputMapper = inputMapper;
    }

    @Override
    public List<ToolSpecResponse> listTools() {
        return toolSpecRegistry.listLlmSelectable().stream()
                .map(spec -> ToolSpecResponse.from(spec, isCallable(spec.name())))
                .toList();
    }

    @Override
    public ToolCallResult call(Long currentUserId, ToolCallRequest request) {
        ValidationUtils.requireNonNull(request, "request");
        ValidationUtils.requireNonBlank(request.getToolName(), "toolName");
        String toolName = request.getToolName().trim();
        ToolSpec spec = getSpec(toolName);
        if (!toolSpecRegistry.isLlmSelectable(toolName)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tool is not visible for tool call: " + toolName);
        }
        if (!isCallable(toolName)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tool is not callable by ToolCall API: " + toolName);
        }
        if (!toolRegistry.contains(toolName)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tool is not registered: " + toolName);
        }
        Map<String, Object> arguments = argumentValidator.validate(currentUserId, spec, request.getArguments());
        Object input = inputMapper.toInput(toolName, arguments);
        return execute(toolName, input);
    }

    public boolean isCallable(String toolName) {
        return CALLABLE_TOOLS.contains(toolName);
    }

    private ToolSpec getSpec(String toolName) {
        try {
            return toolSpecRegistry.get(toolName);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "unknown tool: " + toolName);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ToolCallResult execute(String toolName, Object input) {
        AgentTool tool = toolRegistry.get(toolName);
        long start = System.nanoTime();
        try {
            Object result = tool.execute(input);
            long durationMs = elapsedMs(start);
            if (result instanceof DocumentStatusTool.StatusResult statusResult) {
                return ToolCallResult.success(
                        toolName,
                        statusResult,
                        "parseStatus=" + statusResult.parseStatus() + ", parseReady=" + statusResult.parseReady(),
                        durationMs,
                        List.of(),
                        List.of()
                );
            }
            if (result instanceof DocumentRagQaTool.RagQaResult ragResult) {
                return ToolCallResult.success(
                        toolName,
                        ragResult,
                        ragResult.outputSummary(),
                        durationMs,
                        ragResult.citations(),
                        ragResult.retrievalHits()
                );
            }
            if (result instanceof DocumentSearchTool.SearchResult searchResult) {
                return ToolCallResult.success(
                        toolName,
                        searchResult,
                        searchResult.outputSummary(),
                        durationMs,
                        searchResult.citations(),
                        searchResult.hits()
                );
            }
            if (result instanceof KnowledgeBaseSearchTool.SearchResult searchResult) {
                return ToolCallResult.success(
                        toolName,
                        searchResult,
                        searchResult.outputSummary(),
                        durationMs,
                        searchResult.citations(),
                        searchResult.hits()
                );
            }
            return ToolCallResult.success(toolName, result, "", durationMs, List.of(), List.of());
        } catch (BusinessException ex) {
            return ToolCallResult.failed(toolName, ex.getErrorCode().name(), safeErrorMessage(ex), elapsedMs(start));
        } catch (Exception ex) {
            return ToolCallResult.failed(toolName, ex, elapsedMs(start));
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private String safeErrorMessage(BusinessException ex) {
        if (ex == null || ex.getErrorCode() == null) {
            return "BUSINESS_ERROR";
        }
        return ex.getErrorCode().name();
    }
}
