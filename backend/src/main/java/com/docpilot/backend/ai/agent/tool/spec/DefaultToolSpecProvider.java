package com.docpilot.backend.ai.agent.tool.spec;

import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagTool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DefaultToolSpecProvider implements ToolSpecProvider {

    @Override
    public List<ToolSpec> getToolSpecs() {
        return List.of(
                documentStatusTool(),
                documentSummaryTool(),
                documentQaTool(),
                ragQaTool(),
                legacyRagTool()
        );
    }

    private ToolSpec documentStatusTool() {
        return new ToolSpec(
                "document_status_tool",
                "Document status",
                "Checks whether the current document is parsed and returns basic document status details.",
                ToolParameterSchema.object(linkedMap(
                        "userId", "Long",
                        "documentId", "Long"
                )),
                Set.of("userId", "documentId"),
                ToolResultSchema.object(linkedMap(
                        "documentId", "Long",
                        "title", "String",
                        "parseStatus", "String",
                        "parseReady", "boolean",
                        "summary", "String",
                        "content", "String"
                )),
                ToolRiskLevel.LOW,
                "document_status_tool",
                true
        );
    }

    private ToolSpec documentSummaryTool() {
        return new ToolSpec(
                "document_summary_tool",
                "Document summary",
                "Returns an existing document summary or a short fallback excerpt from parsed document content.",
                ToolParameterSchema.object(linkedMap(
                        "task", "String",
                        "summary", "String",
                        "content", "String"
                )),
                Set.of("task"),
                ToolResultSchema.object(linkedMap(
                        "output", "String",
                        "source", "summary_field|content_fallback|empty"
                )),
                ToolRiskLevel.LOW,
                "document_summary_tool",
                true
        );
    }

    private ToolSpec documentQaTool() {
        return new ToolSpec(
                "document_qa_tool",
                "Document QA",
                "Answers a question about the current document by using parsed content and returns supporting citations.",
                ToolParameterSchema.object(linkedMap(
                        "userId", "Long",
                        "documentId", "Long",
                        "task", "String",
                        "sessionId", "String|null"
                )),
                Set.of("userId", "documentId", "task"),
                ToolResultSchema.object(linkedMap(
                        "answer", "String",
                        "sessionId", "String",
                        "citations", "List"
                )),
                ToolRiskLevel.MEDIUM,
                "document_qa_tool",
                true
        );
    }

    private ToolSpec ragQaTool() {
        return new ToolSpec(
                DocumentRagQaTool.TOOL_NAME,
                "RAG QA",
                "Answers with the T005 RAG retrieval workflow backed by EmbeddingProvider, VectorStoreClient and RagScopeGuard, returning retrieval hits and evidence citations.",
                ToolParameterSchema.object(linkedMap(
                        "userId", "Long",
                        "documentId", "Long",
                        "question", "String",
                        "sessionId", "String|null",
                        "topK", "int|null",
                        "indexVersion", "int|null"
                )),
                Set.of("userId", "documentId", "question"),
                ToolResultSchema.object(linkedMap(
                        "answer", "String",
                        "sessionId", "String",
                        "noEvidence", "boolean",
                        "fallbackUsed", "boolean",
                        "retrievalHits", "List",
                        "citations", "List"
                )),
                ToolRiskLevel.MEDIUM,
                DocumentRagQaTool.TOOL_NAME,
                true
        );
    }

    private ToolSpec legacyRagTool() {
        return new ToolSpec(
                DocumentRagTool.TOOL_NAME,
                "Document RAG retrieval",
                "Showcase-only legacy RAG retrieval with fake embeddings and an in-memory vector store; do not use for the T005+ RAG workflow.",
                ToolParameterSchema.object(linkedMap(
                        "documentId", "Long",
                        "task", "String",
                        "documentText", "String",
                        "topK", "int"
                )),
                Set.of("documentId", "task", "documentText"),
                ToolResultSchema.object(linkedMap(
                        "retrievedChunks", "List",
                        "answerContext", "String"
                )),
                ToolRiskLevel.LOW,
                DocumentRagTool.TOOL_NAME,
                false
        );
    }

    private Map<String, String> linkedMap(String... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("entries must be key/value pairs");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(entries[i], entries[i + 1]);
        }
        return result;
    }
}
