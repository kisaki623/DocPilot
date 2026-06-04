package com.docpilot.backend.ai.agent.tool.spec;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record ToolSpec(String name,
                       String displayName,
                       String description,
                       ToolParameterSchema parameterSchema,
                       Set<String> requiredFields,
                       ToolResultSchema resultSchema,
                       ToolRiskLevel riskLevel,
                       String executorRef,
                       boolean safeForLlmSelection) {

    public ToolSpec {
        name = requireNonBlank(name, "name");
        displayName = requireNonBlank(displayName, "displayName");
        description = requireNonBlank(description, "description");
        if (parameterSchema == null) {
            throw new IllegalArgumentException("parameterSchema must not be null");
        }
        requiredFields = requiredFields == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(requiredFields));
        if (resultSchema == null) {
            throw new IllegalArgumentException("resultSchema must not be null");
        }
        riskLevel = riskLevel == null ? ToolRiskLevel.LOW : riskLevel;
        executorRef = executorRef == null || executorRef.isBlank() ? name : executorRef.trim();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
