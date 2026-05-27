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
        prompt.append("Select one DocPilot document-agent route. Do not answer the task.\n");
        prompt.append("Task: ").append(task.trim()).append("\n");
        prompt.append("State: parseReady=").append(parseReady)
                .append(", hasSummary=").append(hasSummary).append("\n");
        prompt.append("Decision to required tool mapping:\n");
        prompt.append("- status_only -> document_status_tool\n");
        prompt.append("- summary_tool -> document_summary_tool\n");
        prompt.append("- qa_tool -> document_qa_tool\n");
        prompt.append("- rag_tool -> ").append(DocumentRagTool.TOOL_NAME).append("\n");
        prompt.append("Use rag_tool only for explicit RAG, retrieval, topK chunk, similarity, score, or metadata requests.\n");

        prompt.append("Available tools:\n");
        for (ToolDefinition definition : toolDefinitions) {
            prompt.append("- ").append(definition.toolName())
                    .append(": ").append(definition.description())
                    .append("; input=").append(definition.inputSchemaText())
                    .append("; output=").append(definition.outputSchemaText())
                    .append("\n");
        }

        prompt.append("\nSelect exactly one decision from: status_only, summary_tool, qa_tool, rag_tool.\n");
        prompt.append("toolNames must include document_status_tool and the required tool for the decision. ");
        prompt.append("Only select toolNames from the available tools list. ");
        prompt.append("Do not generate SQL. Do not generate system commands. Do not call tools that are not listed.\n");
        prompt.append("Return only compact JSON: ");
        prompt.append("{\"decision\":\"status_only|summary_tool|qa_tool|rag_tool\",");
        prompt.append("\"toolNames\":[\"document_status_tool\",\"required_tool\"],");
        prompt.append("\"routingReason\":\"short route reason\",");
        prompt.append("\"matchedKeywords\":[\"keyword\"],\"confidence\":0.0}\n");
        return prompt.toString();
    }
}
