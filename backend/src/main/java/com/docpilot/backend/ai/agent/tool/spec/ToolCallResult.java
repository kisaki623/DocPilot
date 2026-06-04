package com.docpilot.backend.ai.agent.tool.spec;

public record ToolCallResult(String toolName,
                             ToolCallStatus status,
                             Object result,
                             String outputSummary,
                             String errorType,
                             String errorMessage) {

    public ToolCallResult {
        toolName = requireNonBlank(toolName, "toolName");
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        outputSummary = normalize(outputSummary);
        errorType = normalize(errorType);
        errorMessage = normalize(errorMessage);
    }

    public static ToolCallResult success(String toolName, Object result, String outputSummary) {
        return new ToolCallResult(toolName, ToolCallStatus.SUCCESS, result, outputSummary, "", "");
    }

    public static ToolCallResult failed(String toolName, String errorType, String errorMessage) {
        return new ToolCallResult(toolName, ToolCallStatus.FAILED, null, "", errorType, errorMessage);
    }

    public static ToolCallResult failed(String toolName, Exception failure) {
        String type = failure == null ? "UnknownException" : failure.getClass().getSimpleName();
        return failed(toolName, type, type);
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
