package com.docpilot.backend.ai.agent.tool.spec;

import java.util.List;

public record ToolCallResult(String toolName,
                             ToolCallStatus status,
                             Object result,
                             String outputSummary,
                             String errorType,
                             String errorMessage,
                             long durationMs,
                             List<?> citations,
                             List<?> retrievalHits) {

    public ToolCallResult {
        toolName = requireNonBlank(toolName, "toolName");
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        outputSummary = normalize(outputSummary);
        errorType = normalize(errorType);
        errorMessage = normalize(errorMessage);
        durationMs = Math.max(0L, durationMs);
        citations = citations == null ? List.of() : List.copyOf(citations);
        retrievalHits = retrievalHits == null ? List.of() : List.copyOf(retrievalHits);
    }

    public ToolCallResult(String toolName,
                          ToolCallStatus status,
                          Object result,
                          String outputSummary,
                          String errorType,
                          String errorMessage) {
        this(toolName, status, result, outputSummary, errorType, errorMessage, 0L, List.of(), List.of());
    }

    public static ToolCallResult success(String toolName, Object result, String outputSummary) {
        return success(toolName, result, outputSummary, 0L, List.of(), List.of());
    }

    public static ToolCallResult success(String toolName,
                                         Object result,
                                         String outputSummary,
                                         long durationMs,
                                         List<?> citations,
                                         List<?> retrievalHits) {
        return new ToolCallResult(toolName, ToolCallStatus.SUCCESS, result, outputSummary, "", "", durationMs, citations, retrievalHits);
    }

    public static ToolCallResult failed(String toolName, String errorType, String errorMessage) {
        return failed(toolName, errorType, errorMessage, 0L);
    }

    public static ToolCallResult failed(String toolName, String errorType, String errorMessage, long durationMs) {
        return new ToolCallResult(toolName, ToolCallStatus.FAILED, null, "", errorType, errorMessage, durationMs, List.of(), List.of());
    }

    public static ToolCallResult failed(String toolName, Exception failure) {
        String type = failure == null ? "UnknownException" : failure.getClass().getSimpleName();
        return failed(toolName, type, type);
    }

    public static ToolCallResult failed(String toolName, Exception failure, long durationMs) {
        String type = failure == null ? "UnknownException" : failure.getClass().getSimpleName();
        return failed(toolName, type, type, durationMs);
    }

    public boolean success() {
        return ToolCallStatus.SUCCESS.equals(status);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
