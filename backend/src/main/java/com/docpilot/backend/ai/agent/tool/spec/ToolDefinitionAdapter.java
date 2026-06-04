package com.docpilot.backend.ai.agent.tool.spec;

import com.docpilot.backend.ai.agent.tool.ToolDefinition;

public class ToolDefinitionAdapter {

    public ToolDefinition toDefinition(ToolSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return new ToolDefinition(
                spec.name(),
                spec.displayName(),
                spec.description(),
                spec.parameterSchema().toSchemaText(),
                spec.resultSchema().toSchemaText(),
                spec.safeForLlmSelection()
        );
    }
}
