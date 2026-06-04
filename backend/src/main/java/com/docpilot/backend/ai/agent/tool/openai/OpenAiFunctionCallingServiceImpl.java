package com.docpilot.backend.ai.agent.tool.openai;

import com.docpilot.backend.ai.agent.dto.ToolCallRequest;
import com.docpilot.backend.ai.agent.service.ToolCallService;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
import com.docpilot.backend.ai.agent.tool.spec.ToolSpecRegistry;
import com.docpilot.backend.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OpenAiFunctionCallingServiceImpl implements OpenAiFunctionCallingService {

    private final ToolSpecRegistry toolSpecRegistry;
    private final ToolCallService toolCallService;
    private final OpenAiToolSchemaAdapter schemaAdapter;
    private final OpenAiToolCallParser toolCallParser;
    private final OpenAiToolResultAdapter resultAdapter;

    public OpenAiFunctionCallingServiceImpl(ToolSpecRegistry toolSpecRegistry,
                                            ToolCallService toolCallService,
                                            OpenAiToolSchemaAdapter schemaAdapter,
                                            OpenAiToolCallParser toolCallParser,
                                            OpenAiToolResultAdapter resultAdapter) {
        this.toolSpecRegistry = toolSpecRegistry;
        this.toolCallService = toolCallService;
        this.schemaAdapter = schemaAdapter;
        this.toolCallParser = toolCallParser;
        this.resultAdapter = resultAdapter;
    }

    @Override
    public OpenAiFunctionCallingResult callTools(Long currentUserId, String userMessage, String mockModelResponseJson) {
        List<OpenAiToolDefinition> tools = schemaAdapter.toTools(toolSpecRegistry.listLlmSelectable());
        List<OpenAiParsedToolCall> toolCalls;
        try {
            toolCalls = toolCallParser.parse(mockModelResponseJson);
        } catch (BusinessException ex) {
            return OpenAiFunctionCallingResult.failed(userMessage, tools, ex.getErrorCode().name(), safeErrorMessage(ex));
        } catch (Exception ex) {
            return OpenAiFunctionCallingResult.failed(userMessage, tools, ex.getClass().getSimpleName(), ex.getClass().getSimpleName());
        }

        List<ToolCallResult> results = new ArrayList<>();
        List<OpenAiToolMessage> messages = new ArrayList<>();
        for (OpenAiParsedToolCall call : toolCalls) {
            ToolCallResult result = executeTool(currentUserId, call);
            results.add(result);
            messages.add(resultAdapter.toToolMessage(call, result));
        }
        boolean success = results.stream().allMatch(ToolCallResult::success);
        if (success) {
            return OpenAiFunctionCallingResult.success(userMessage, tools, toolCalls, results, messages);
        }
        return new OpenAiFunctionCallingResult(
                false,
                userMessage,
                tools,
                toolCalls,
                results,
                messages,
                "TOOL_CALL_FAILED",
                "One or more tool calls failed"
        );
    }

    private ToolCallResult executeTool(Long currentUserId, OpenAiParsedToolCall call) {
        ToolCallRequest request = new ToolCallRequest();
        request.setToolName(call.toolName());
        request.setArguments(call.arguments());
        try {
            return toolCallService.call(currentUserId, request);
        } catch (BusinessException ex) {
            return ToolCallResult.failed(call.toolName(), ex.getErrorCode().name(), safeErrorMessage(ex));
        } catch (Exception ex) {
            return ToolCallResult.failed(call.toolName(), ex);
        }
    }

    private String safeErrorMessage(BusinessException ex) {
        if (ex == null || ex.getErrorCode() == null) {
            return "BUSINESS_ERROR";
        }
        return ex.getErrorCode().name();
    }
}
