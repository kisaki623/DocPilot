package com.docpilot.backend.ai.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolDefinitionProvider {

    private final ToolRegistry toolRegistry;
    private final Map<String, ToolDefinition> definitions;

    public ToolDefinitionProvider(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        this.definitions = buildDefinitions();
    }

    public List<ToolDefinition> getAllDefinitions() {
        return toolRegistry.getToolNames().stream()
                .map(definitions::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public ToolDefinition getByToolName(String toolName) {
        ToolDefinition definition = definitions.get(toolName);
        if (definition == null || !toolRegistry.getToolNames().contains(toolName)) {
            throw new IllegalArgumentException("Unknown agent tool definition: " + toolName);
        }
        return definition;
    }

    private Map<String, ToolDefinition> buildDefinitions() {
        Map<String, ToolDefinition> result = new LinkedHashMap<>();
        result.put("document_status_tool", new ToolDefinition(
                "document_status_tool",
                "Document status",
                "Checks whether the current document is parsed and returns basic document status details.",
                "{\"userId\":\"Long\",\"documentId\":\"Long\"}",
                "{\"documentId\":\"Long\",\"title\":\"String\",\"parseStatus\":\"String\",\"parseReady\":\"boolean\",\"summary\":\"String\",\"content\":\"String\"}",
                true
        ));
        result.put("document_summary_tool", new ToolDefinition(
                "document_summary_tool",
                "Document summary",
                "Returns an existing document summary or a short fallback excerpt from parsed document content.",
                "{\"task\":\"String\",\"summary\":\"String\",\"content\":\"String\"}",
                "{\"output\":\"String\",\"source\":\"summary_field|content_fallback|empty\"}",
                true
        ));
        result.put("document_qa_tool", new ToolDefinition(
                "document_qa_tool",
                "Document QA",
                "Answers a question about the current document by using parsed content and returns supporting citations.",
                "{\"userId\":\"Long\",\"documentId\":\"Long\",\"task\":\"String\",\"sessionId\":\"String|null\"}",
                "{\"answer\":\"String\",\"sessionId\":\"String\",\"citations\":[{\"chunkIndex\":\"int\",\"snippet\":\"String\",\"score\":\"double\"}]}",
                true
        ));
        return Map.copyOf(result);
    }
}
