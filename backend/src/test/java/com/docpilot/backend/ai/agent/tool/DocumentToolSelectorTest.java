package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentToolSelectorTest {

    private static final List<String> STATUS_ONLY = List.of("document_status_tool");
    private static final List<String> SUMMARY_CHAIN = List.of("document_status_tool", "document_summary_tool");
    private static final List<String> QA_CHAIN = List.of("document_status_tool", "document_qa_tool");

    private final DocumentToolSelector selector = new DocumentToolSelector();

    @Test
    void shouldExplainStatusOnlySelection() {
        ToolSelector.SelectResult result = selector.select("\u67e5\u770b\u6587\u6863\u89e3\u6790\u72b6\u6001");

        assertEquals("status_only", result.decision());
        assertEquals(STATUS_ONLY, result.toolNames());
        assertFalse(result.matchedKeywords().isEmpty());
        assertTrue(result.reason().contains("\u72b6\u6001") || result.reason().contains("\u89e3\u6790"));
    }

    @Test
    void shouldExplainSummarySelection() {
        ToolSelector.SelectResult result = selector.select("\u603b\u7ed3\u4e00\u4e0b\u8fd9\u7bc7\u6587\u6863");

        assertEquals("summary_tool", result.decision());
        assertEquals(SUMMARY_CHAIN, result.toolNames());
        assertTrue(result.matchedKeywords().contains("\u603b\u7ed3"));
        assertFalse(result.reason().isBlank());
    }

    @Test
    void shouldExplainEvidenceSelectionAsQa() {
        ToolSelector.SelectResult result = selector.select("\u6839\u636e\u539f\u6587\u8bc1\u636e\u56de\u7b54\u5408\u540c\u91d1\u989d\u662f\u591a\u5c11");

        assertEquals("qa_tool", result.decision());
        assertEquals(QA_CHAIN, result.toolNames());
        assertFalse(result.matchedKeywords().isEmpty());
        assertTrue(result.reason().contains("\u8bc1\u636e") || result.reason().contains("\u5f15\u7528"));
    }

    @Test
    void shouldExplainDefaultQaSelectionWithoutMatchedKeywords() {
        ToolSelector.SelectResult result = selector.select("\u8fd9\u4e2a\u6587\u6863\u4e3b\u8981\u8bb2\u4e86\u54ea\u4e9b\u7ec6\u8282");

        assertEquals("qa_tool", result.decision());
        assertEquals(QA_CHAIN, result.toolNames());
        assertTrue(result.matchedKeywords().isEmpty());
        assertFalse(result.reason().isBlank());
        assertTrue(result.reason().contains("\u9ed8\u8ba4") || result.reason().contains("\u95ee\u7b54"));
    }

    @Test
    void shouldPreferQaWhenSummaryAndEvidenceConflict() {
        ToolSelector.SelectResult result = selector.select("\u603b\u7ed3\u5e76\u5f15\u7528\u539f\u6587\u8bc1\u636e");

        assertEquals("qa_tool", result.decision());
        assertEquals(QA_CHAIN, result.toolNames());
        assertFalse(result.matchedKeywords().isEmpty());
        assertTrue(result.reason().contains("\u8bc1\u636e") || result.reason().contains("\u5f15\u7528"));
    }

    @Test
    void shouldMatchEnglishKeywordsCaseInsensitively() {
        ToolSelector.SelectResult result = selector.select("Please SUMMARY this document with EVIDENCE");

        assertEquals("qa_tool", result.decision());
        assertEquals(QA_CHAIN, result.toolNames());
        assertEquals(List.of("evidence"), result.matchedKeywords());
        assertFalse(result.reason().isBlank());
    }

    @Test
    void shouldDefaultBlankInputToQa() {
        assertDefaultQa(selector.select(""));
        assertDefaultQa(selector.select("   "));
    }

    @Test
    void shouldDefaultNullInputToQa() {
        assertDefaultQa(selector.select(null));
    }

    private void assertDefaultQa(ToolSelector.SelectResult result) {
        assertEquals("qa_tool", result.decision());
        assertEquals(QA_CHAIN, result.toolNames());
        assertTrue(result.matchedKeywords().isEmpty());
        assertFalse(result.reason().isBlank());
    }
}
