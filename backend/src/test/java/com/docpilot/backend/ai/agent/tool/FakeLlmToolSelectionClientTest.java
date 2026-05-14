package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FakeLlmToolSelectionClientTest {

    private final FakeLlmToolSelectionClient client = new FakeLlmToolSelectionClient();
    private final LlmToolSelectionParser parser = new LlmToolSelectionParser(Set.of(
            "document_status_tool",
            "document_summary_tool",
            "document_qa_tool"
    ));

    @Test
    void shouldReturnSummaryDecisionForSummaryPrompt() {
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt(
                "Current task: Please summarize this document\nDocument state:\n- parseReady: true"
        );

        LlmToolSelectionResult result = parser.parse(response.rawText());

        assertThat(response.provider()).isEqualTo("fake");
        assertThat(response.model()).isEqualTo("fake-selector");
        assertThat(response.disabled()).isFalse();
        assertThat(response.errorMessage()).isEmpty();
        assertThat(result.decision()).isEqualTo("summary_tool");
        assertThat(result.toolNames()).containsExactly("document_status_tool", "document_summary_tool");
    }

    @Test
    void shouldReturnQaDecisionForQuestionPrompt() {
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt(
                "Current task: According to the evidence, what is the core content?\nDocument state:\n- parseReady: true"
        );

        LlmToolSelectionResult result = parser.parse(response.rawText());

        assertThat(response.disabled()).isFalse();
        assertThat(result.decision()).isEqualTo("qa_tool");
        assertThat(result.toolNames()).containsExactly("document_status_tool", "document_qa_tool");
    }

    @Test
    void shouldReturnStatusDecisionForStatusPrompt() {
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt(
                "Current task: Check parse status for this document\nDocument state:\n- parseReady: false"
        );

        LlmToolSelectionResult result = parser.parse(response.rawText());

        assertThat(response.disabled()).isFalse();
        assertThat(result.decision()).isEqualTo("status_only");
        assertThat(result.toolNames()).containsExactly("document_status_tool");
    }

    @Test
    void shouldNotNeedApiKeyForBlankPrompt() {
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt("");

        LlmToolSelectionResult result = parser.parse(response.rawText());

        assertThat(response.provider()).isEqualTo("fake");
        assertThat(response.model()).isEqualTo("fake-selector");
        assertThat(response.disabled()).isFalse();
        assertThat(result.decision()).isEqualTo("qa_tool");
    }
}
