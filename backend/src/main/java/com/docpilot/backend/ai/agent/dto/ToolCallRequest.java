package com.docpilot.backend.ai.agent.dto;

import java.util.Map;

public class ToolCallRequest {

    private String toolName;
    private Map<String, Object> arguments;

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
