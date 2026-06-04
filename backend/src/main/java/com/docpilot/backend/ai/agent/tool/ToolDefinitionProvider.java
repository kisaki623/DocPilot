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
        result.put(DocumentRagQaTool.TOOL_NAME, new ToolDefinition(
                DocumentRagQaTool.TOOL_NAME,
                "RAG QA",
                "Answers with the T005 RAG retrieval workflow backed by EmbeddingProvider and VectorStoreClient, returning retrieval hits and evidence citations.",
                "{\"userId\":\"Long\",\"documentId\":\"Long\",\"question\":\"String\",\"sessionId\":\"String|null\",\"topK\":\"int|null\",\"indexVersion\":\"int|null\"}",
                "{\"answer\":\"String\",\"sessionId\":\"String\",\"noEvidence\":\"boolean\",\"fallbackUsed\":\"boolean\",\"retrievalHits\":\"List\",\"citations\":\"List\"}",
                true
        ));
        result.put(DocumentRagTool.TOOL_NAME, new ToolDefinition(
                DocumentRagTool.TOOL_NAME,
                "Document RAG retrieval",
                "Showcase-only legacy RAG retrieval with fake embeddings and an in-memory vector store; do not use for the T005/T006 RAG workflow.",
                "{\"documentId\":\"Long\",\"task\":\"String\",\"documentText\":\"String\",\"topK\":\"int\"}",
                "{\"retrievedChunks\":[{\"rank\":\"int\",\"chunkIndex\":\"int\",\"score\":\"double\",\"snippet\":\"String\",\"metadata\":\"Map\"}],\"answerContext\":\"String\"}",
                true
        ));
        return Map.copyOf(result);
    }
}
