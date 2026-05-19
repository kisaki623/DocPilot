package com.docpilot.backend.ai.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LlmToolSelectionPromptBuilder {

    public String build(String task,
                        boolean parseReady,
                        boolean hasSummary,
                        List<ToolDefinition> toolDefinitions) {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("task must not be blank");
        }
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            throw new IllegalArgumentException("toolDefinitions must not be empty");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are selecting tools for DocPilot's document agent.\n");
        prompt.append("Current task: ").append(task.trim()).append("\n");
        prompt.append("Document state:\n");
        prompt.append("- parseReady: ").append(parseReady).append("\n");
        prompt.append("- hasSummary: ").append(hasSummary).append("\n\n");

        prompt.append("Available tools:\n");
        for (ToolDefinition definition : toolDefinitions) {
            prompt.append("- toolName: ").append(definition.toolName()).append("\n");
            prompt.append("  displayName: ").append(definition.displayName()).append("\n");
            prompt.append("  description: ").append(definition.description()).append("\n");
            prompt.append("  inputSchemaText: ").append(definition.inputSchemaText()).append("\n");
            prompt.append("  outputSchemaText: ").append(definition.outputSchemaText()).append("\n");
            prompt.append("  safeForLlmSelection: ").append(definition.safeForLlmSelection()).append("\n");
        }

        prompt.append("\nSelect exactly one decision from: status_only, summary_tool, qa_tool, rag_tool.\n");
        prompt.append("Only select toolNames from the available tools list. ");
        prompt.append("Do not generate SQL. Do not generate system commands. Do not call tools that are not listed.\n");
        prompt.append("Return only one JSON object using this exact shape:\n");
        prompt.append("{\n");
        prompt.append("  \"decision\": \"status_only|summary_tool|qa_tool|rag_tool\",\n");
        prompt.append("  \"toolNames\": [\"document_status_tool\"],\n");
        prompt.append("  \"routingReason\": \"short reason for the route\",\n");
        prompt.append("  \"matchedKeywords\": [\"keyword\"],\n");
        prompt.append("  \"confidence\": 0.0\n");
        prompt.append("}\n");
        return prompt.toString();
    }
}
