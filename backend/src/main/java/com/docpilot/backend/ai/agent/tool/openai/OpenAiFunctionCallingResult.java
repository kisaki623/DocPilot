package com.docpilot.backend.ai.agent.tool.openai;

import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;

import java.util.List;

public record OpenAiFunctionCallingResult(boolean success,
                                          String userMessage,
                                          List<OpenAiToolDefinition> tools,
                                          List<OpenAiParsedToolCall> toolCalls,
                                          List<ToolCallResult> toolResults,
                                          List<OpenAiToolMessage> toolMessages,
                                          String errorType,
                                          String errorMessage) {

    public OpenAiFunctionCallingResult {
        userMessage = userMessage == null ? "" : userMessage;
        tools = tools == null ? List.of() : List.copyOf(tools);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        toolMessages = toolMessages == null ? List.of() : List.copyOf(toolMessages);
        errorType = errorType == null ? "" : errorType.trim();
        errorMessage = errorMessage == null ? "" : errorMessage.trim();
    }

    public static OpenAiFunctionCallingResult success(String userMessage,
                                                      List<OpenAiToolDefinition> tools,
                                                      List<OpenAiParsedToolCall> toolCalls,
                                                      List<ToolCallResult> toolResults,
                                                      List<OpenAiToolMessage> toolMessages) {
        return new OpenAiFunctionCallingResult(true, userMessage, tools, toolCalls, toolResults, toolMessages, "", "");
    }

    public static OpenAiFunctionCallingResult failed(String userMessage,
                                                     List<OpenAiToolDefinition> tools,
                                                     String errorType,
                                                     String errorMessage) {
        return new OpenAiFunctionCallingResult(false, userMessage, tools, List.of(), List.of(), List.of(), errorType, errorMessage);
    }
}
