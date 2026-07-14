package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmToolSelectionPromptBuilderTest {

    private final LlmToolSelectionPromptBuilder builder = new LlmToolSelectionPromptBuilder();
    private final List<ToolDefinition> definitions = List.of(
            new ToolDefinition(
                    "document_status_tool",
                    "Document status",
                    "Checks parse status.",
                    "{\"documentId\":\"Long\"}",
                    "{\"parseReady\":\"boolean\"}",
                    true
            ),
            new ToolDefinition(
                    "document_summary_tool",
                    "Document summary",
                    "Returns summary.",
                    "{\"task\":\"String\"}",
                    "{\"output\":\"String\"}",
                    true
            ),
            new ToolDefinition(
                    "document_qa_tool",
                    "Document QA",
                    "Answers with citations.",
                    "{\"task\":\"String\"}",
                    "{\"answer\":\"String\",\"citations\":[]}",
                    true
            ),
            new ToolDefinition(
                    DocumentSearchTool.TOOL_NAME,
                    "Document search",
                    "Retrieves evidence chunks.",
                    "{\"query\":\"String\"}",
                    "{\"hits\":[],\"citations\":[]}",
                    true
            ),
            new ToolDefinition(
                    DocumentRagQaTool.TOOL_NAME,
                    "RAG QA",
                    "Answers with retrieval hits and citations.",
                    "{\"task\":\"String\"}",
                    "{\"retrievalHits\":[],\"citations\":[]}",
                    true
            )
    );

    @Test
    void shouldIncludeToolNames() {
        String prompt = buildPrompt();

        assertTrue(prompt.contains("document_status_tool"));
        assertTrue(prompt.contains("document_summary_tool"));
        assertTrue(prompt.contains("document_qa_tool"));
        assertTrue(prompt.contains(DocumentSearchTool.TOOL_NAME));
        assertTrue(prompt.contains(DocumentRagQaTool.TOOL_NAME));
    }

    @Test
    void shouldIncludeJsonOutputFormat() {
        String prompt = buildPrompt();

        assertTrue(prompt.contains("\"decision\""));
        assertTrue(prompt.contains("\"toolNames\""));
        assertTrue(prompt.contains("\"routingReason\""));
        assertTrue(prompt.contains("\"matchedKeywords\""));
        assertTrue(prompt.contains("\"confidence\""));
    }

    @Test
    void shouldIncludeSafetyRestrictions() {
        String prompt = buildPrompt();

        assertTrue(prompt.contains("Do not generate SQL"));
        assertTrue(prompt.contains("Do not generate system commands"));
        assertTrue(prompt.contains("Do not call tools that are not listed"));
    }

    @Test
    void shouldIncludeTaskAndDocumentState() {
        String prompt = buildPrompt();

        assertTrue(prompt.contains("Summarize with evidence"));
        assertTrue(prompt.contains("parseReady=true"));
        assertTrue(prompt.contains("hasSummary=false"));
    }

    @Test
    void shouldLimitDecisionValues() {
        String prompt = buildPrompt();

        assertTrue(prompt.contains("status_only"));
        assertTrue(prompt.contains("summary_tool"));
        assertTrue(prompt.contains("qa_tool"));
        assertTrue(prompt.contains("search_tool"));
        assertTrue(prompt.contains("rag_tool"));
    }

    @Test
    void shouldConstrainRagDecisionToRegisteredRagTool() {
        String prompt = buildPrompt();

        assertTrue(prompt.contains("search_tool -> " + DocumentSearchTool.TOOL_NAME));
        assertTrue(prompt.contains("rag_tool -> " + DocumentRagQaTool.TOOL_NAME));
        assertTrue(prompt.contains("Use search_tool for retrieval-only"));
        assertTrue(prompt.contains("Use rag_tool when the task asks to answer"));
        assertTrue(prompt.contains("toolNames must include document_status_tool and the required tool"));
    }

    private String buildPrompt() {
        return builder.build("Summarize with evidence", true, false, definitions);
    }
}
