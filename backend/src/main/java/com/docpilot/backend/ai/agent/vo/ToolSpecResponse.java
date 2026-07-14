package com.docpilot.backend.ai.agent.vo;

import com.docpilot.backend.ai.agent.tool.spec.ToolParameterSchema;
import com.docpilot.backend.ai.agent.tool.spec.ToolResultSchema;
import com.docpilot.backend.ai.agent.tool.spec.ToolRiskLevel;
import com.docpilot.backend.ai.agent.tool.spec.ToolSpec;

import java.util.Set;

public class ToolSpecResponse {

    private String name;
    private String displayName;
    private String description;
    private ToolParameterSchema parameterSchema;
    private Set<String> requiredFields;
    private ToolResultSchema resultSchema;
    private ToolRiskLevel riskLevel;
    private boolean safeForLlmSelection;
    private boolean callableByToolCallApi;

    public static ToolSpecResponse from(ToolSpec spec, boolean callableByToolCallApi) {
        ToolSpecResponse response = new ToolSpecResponse();
        response.setName(spec.name());
        response.setDisplayName(spec.displayName());
        response.setDescription(spec.description());
        response.setParameterSchema(spec.parameterSchema());
        response.setRequiredFields(spec.requiredFields());
        response.setResultSchema(spec.resultSchema());
        response.setRiskLevel(spec.riskLevel());
        response.setSafeForLlmSelection(spec.safeForLlmSelection());
        response.setCallableByToolCallApi(callableByToolCallApi);
        return response;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ToolParameterSchema getParameterSchema() {
        return parameterSchema;
    }

    public void setParameterSchema(ToolParameterSchema parameterSchema) {
        this.parameterSchema = parameterSchema;
    }

    public Set<String> getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(Set<String> requiredFields) {
        this.requiredFields = requiredFields;
    }

    public ToolResultSchema getResultSchema() {
        return resultSchema;
    }

    public void setResultSchema(ToolResultSchema resultSchema) {
        this.resultSchema = resultSchema;
    }

    public ToolRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(ToolRiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public boolean isSafeForLlmSelection() {
        return safeForLlmSelection;
    }

    public void setSafeForLlmSelection(boolean safeForLlmSelection) {
        this.safeForLlmSelection = safeForLlmSelection;
    }

    public boolean isCallableByToolCallApi() {
        return callableByToolCallApi;
    }

    public void setCallableByToolCallApi(boolean callableByToolCallApi) {
        this.callableByToolCallApi = callableByToolCallApi;
    }
}
