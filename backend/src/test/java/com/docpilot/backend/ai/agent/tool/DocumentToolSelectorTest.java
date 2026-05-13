package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentToolSelectorTest {

    private final DocumentToolSelector selector = new DocumentToolSelector();

    @Test
    void shouldSelectStatusOnlyForStatusIntent() {
        assertSelection("查看文档解析状态", "status_only", List.of("document_status_tool"));
        assertSelection("这个文档是否完成解析", "status_only", List.of("document_status_tool"));
    }

    @Test
    void shouldSelectSummaryToolForSummaryIntent() {
        assertSelection("总结一下这篇文档", "summary_tool", List.of("document_status_tool", "document_summary_tool"));
        assertSelection("帮我生成文档摘要", "summary_tool", List.of("document_status_tool", "document_summary_tool"));
    }

    @Test
    void shouldSelectQaToolForEvidenceIntent() {
        assertSelection("请引用原文证据说明合同金额是多少", "qa_tool", List.of("document_status_tool", "document_qa_tool"));
        assertSelection("根据证据说明文档里有没有提到风险条款", "qa_tool", List.of("document_status_tool", "document_qa_tool"));
    }

    @Test
    void shouldSelectQaToolForDefaultQuestion() {
        assertSelection("这个文档主要讲了什么细节", "qa_tool", List.of("document_status_tool", "document_qa_tool"));
    }

    @Test
    void shouldPreferQaWhenSummaryNeedsEvidence() {
        assertSelection("总结并引用原文证据", "qa_tool", List.of("document_status_tool", "document_qa_tool"));
    }

    @Test
    void shouldSelectQaToolForBlankInput() {
        assertSelection("", "qa_tool", List.of("document_status_tool", "document_qa_tool"));
        assertSelection("   ", "qa_tool", List.of("document_status_tool", "document_qa_tool"));
    }

    private void assertSelection(String task, String expectedDecision, List<String> expectedToolNames) {
        ToolSelector.SelectResult result = selector.select(task);

        assertEquals(expectedDecision, result.decision());
        assertEquals(expectedToolNames, result.toolNames());
    }
}
