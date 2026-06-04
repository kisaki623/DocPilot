package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FakeLlmToolSelectionClientTest {

    private final FakeLlmToolSelectionClient client = new FakeLlmToolSelectionClient();
    private final LlmToolSelectionParser parser = new LlmToolSelectionParser(Set.of(
            "document_status_tool",
            "document_summary_tool",
            "document_qa_tool",
            DocumentRagQaTool.TOOL_NAME
    ));

    @Test
    void shouldExtractSummaryTaskFromPrompt() {
        LlmToolSelectionResult result = parse(
                "Current task: Please summarize this document\nDocument state:\n- parseReady: true"
        );

        assertThat(result.decision()).isEqualTo("summary_tool");
        assertThat(result.toolNames()).containsExactly("document_status_tool", "document_summary_tool");
        assertValidCommonFields(result);
    }

    @Test
    void shouldExtractRagEvidenceTaskFromPrompt() {
        LlmToolSelectionResult result = parse(
                "Current task: According to the evidence, what is the core content?\nDocument state:\n- parseReady: true"
        );

        assertThat(result.decision()).isEqualTo("rag_tool");
        assertThat(result.toolNames()).containsExactly("document_status_tool", DocumentRagQaTool.TOOL_NAME);
        assertValidCommonFields(result);
    }

    @Test
    void shouldExtractStatusTaskFromPrompt() {
        LlmToolSelectionResult result = parse(
                "Current task: What is the parsing progress?\nDocument state:\n- parseReady: false"
        );

        assertThat(result.decision()).isEqualTo("status_only");
        assertThat(result.toolNames()).containsExactly("document_status_tool");
        assertValidCommonFields(result);
    }

    @Test
    void shouldPreferRagWhenSummaryAndEvidenceConflict() {
        LlmToolSelectionResult result = parse(
                "Current task: summary with evidence\nDocument state:\n- parseReady: true"
        );

        assertThat(result.decision()).isEqualTo("rag_tool");
        assertThat(result.toolNames()).containsExactly("document_status_tool", DocumentRagQaTool.TOOL_NAME);
        assertValidCommonFields(result);
    }

    @Test
    void shouldSupportChineseSummaryTask() {
        LlmToolSelectionResult result = parse(
                "Current task: \u6982\u89c8\u4e00\u4e0b\u5185\u5bb9\nDocument state:\n- parseReady: true"
        );

        assertThat(result.decision()).isEqualTo("summary_tool");
        assertThat(result.toolNames()).containsExactly("document_status_tool", "document_summary_tool");
        assertValidCommonFields(result);
    }

    @Test
    void shouldSupportChineseEvidenceTask() {
        LlmToolSelectionResult result = parse(
                "Current task: \u6839\u636e\u539f\u6587\u8bc1\u636e\u56de\u7b54\u6838\u5fc3\u5185\u5bb9\nDocument state:\n- parseReady: true"
        );

        assertThat(result.decision()).isEqualTo("rag_tool");
        assertThat(result.toolNames()).containsExactly("document_status_tool", DocumentRagQaTool.TOOL_NAME);
        assertValidCommonFields(result);
    }

    @Test
    void shouldTreatEnglishCaseInsensitively() {
        LlmToolSelectionResult result = parse(
                "Current task: Document STATE please\nDocument state:\n- parseReady: true"
        );

        assertThat(result.decision()).isEqualTo("status_only");
        assertThat(result.toolNames()).containsExactly("document_status_tool");
        assertValidCommonFields(result);
    }

    @Test
    void shouldDefaultBlankInputToQaWithoutApiKey() {
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt("");
        LlmToolSelectionResult result = parser.parse(response.rawText());

        assertThat(response.provider()).isEqualTo("fake");
        assertThat(response.model()).isEqualTo("fake-selector");
        assertThat(response.disabled()).isFalse();
        assertThat(response.errorMessage()).isEmpty();
        assertThat(result.decision()).isEqualTo("qa_tool");
        assertThat(result.toolNames()).containsExactly("document_status_tool", "document_qa_tool");
        assertValidCommonFields(result);
    }

    @Test
    void shouldUseTaskOnlyWhenPromptContainsOtherToolText() {
        LlmToolSelectionResult result = parse("""
                You are selecting tools.
                Current task: brief overview
                Available tools:
                - toolName: document_qa_tool
                  description: evidence citation proof
                """);

        assertThat(result.decision()).isEqualTo("summary_tool");
        assertThat(result.toolNames()).containsExactly("document_status_tool", "document_summary_tool");
        assertValidCommonFields(result);
    }

    @Test
    void shouldExtractTaskFromCompactPromptFormat() {
        LlmToolSelectionResult result = parse("""
                Select one DocPilot document-agent route. Do not answer the task.
                Task: brief overview
                State: parseReady=true, hasSummary=true
                Decision to required tool mapping:
                - qa_tool -> document_qa_tool
                """);

        assertThat(result.decision()).isEqualTo("summary_tool");
        assertThat(result.toolNames()).containsExactly("document_status_tool", "document_summary_tool");
        assertValidCommonFields(result);
    }

    private LlmToolSelectionResult parse(String prompt) {
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt(prompt);
        assertThat(response.provider()).isEqualTo("fake");
        assertThat(response.model()).isEqualTo("fake-selector");
        assertThat(response.disabled()).isFalse();
        assertThat(response.errorMessage()).isEmpty();
        return parser.parse(response.rawText());
    }

    private void assertValidCommonFields(LlmToolSelectionResult result) {
        assertThat(result.routingReason()).isNotBlank();
        assertThat(result.matchedKeywords()).isNotEmpty();
        assertThat(result.confidence()).isBetween(0.0d, 1.0d);
        assertThat(result.toolNames()).allSatisfy(toolName ->
                assertThat(toolName).isIn("document_status_tool", "document_summary_tool", "document_qa_tool", DocumentRagQaTool.TOOL_NAME)
        );
    }
}
