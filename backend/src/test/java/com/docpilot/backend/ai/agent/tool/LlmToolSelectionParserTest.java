package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmToolSelectionParserTest {

    private final LlmToolSelectionParser parser = new LlmToolSelectionParser(Set.of(
            "document_status_tool",
            "document_summary_tool",
            "document_qa_tool",
            DocumentSearchTool.TOOL_NAME
    ));

    @Test
    void shouldParseStandardJson() {
        LlmToolSelectionResult result = parser.parse("""
                {
                  "decision": "summary_tool",
                  "toolNames": ["document_status_tool", "document_summary_tool"],
                  "routingReason": "The task asks for a summary.",
                  "matchedKeywords": ["summary"],
                  "confidence": 0.82
                }
                """);

        assertEquals("summary_tool", result.decision());
        assertEquals(List.of("document_status_tool", "document_summary_tool"), result.toolNames());
        assertEquals("The task asks for a summary.", result.routingReason());
        assertEquals(List.of("summary"), result.matchedKeywords());
        assertEquals(0.82d, result.confidence());
    }

    @Test
    void shouldExtractJsonWhenTextSurroundsIt() {
        LlmToolSelectionResult result = parser.parse("""
                I will choose the QA tool.
                {"decision":"qa_tool","toolNames":["document_status_tool","document_qa_tool"],"routingReason":"Evidence is requested.","matchedKeywords":["evidence"],"confidence":0.91}
                Done.
                """);

        assertEquals("qa_tool", result.decision());
        assertEquals(List.of("document_status_tool", "document_qa_tool"), result.toolNames());
    }

    @Test
    void shouldRejectIllegalDecision() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {"decision":"delete_tool","toolNames":["document_status_tool"],"routingReason":"bad","matchedKeywords":[],"confidence":0.4}
                """));
    }

    @Test
    void shouldParseSearchToolDecision() {
        LlmToolSelectionResult result = parser.parse("""
                {"decision":"search_tool","toolNames":["document_status_tool","document_search_tool"],"routingReason":"Retrieval only.","matchedKeywords":["retrieve"],"confidence":0.88}
                """);

        assertEquals("search_tool", result.decision());
        assertEquals(List.of("document_status_tool", DocumentSearchTool.TOOL_NAME), result.toolNames());
    }

    @Test
    void shouldRejectUnknownToolName() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {"decision":"status_only","toolNames":["unknown_tool"],"routingReason":"bad","matchedKeywords":[],"confidence":0.4}
                """));
    }

    @Test
    void shouldRejectOutOfRangeConfidence() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {"decision":"status_only","toolNames":["document_status_tool"],"routingReason":"bad","matchedKeywords":[],"confidence":1.4}
                """));
    }

    @Test
    void shouldRejectBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   "));
    }

    @Test
    void shouldRejectEmptyToolNames() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {"decision":"status_only","toolNames":[],"routingReason":"bad","matchedKeywords":[],"confidence":0.4}
                """));
    }
}
