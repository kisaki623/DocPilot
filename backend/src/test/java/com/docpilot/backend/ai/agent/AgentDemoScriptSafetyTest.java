package com.docpilot.backend.ai.agent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDemoScriptSafetyTest {

    @Test
    void shouldKeepShowcaseDemoSummarySanitized() throws Exception {
        String script = Files.readString(Path.of("scripts", "agent", "demo-agent-showcase.ps1"),
                StandardCharsets.UTF_8);

        assertThat(script)
                .contains("backendReachable")
                .contains("authTokenPresent")
                .contains("documentIdPresent")
                .contains("agentRunOk")
                .contains("decision")
                .contains("ragRetrievedCount")
                .contains("citationCount")
                .contains("traceStepCount")
                .contains("remote-redacted")
                .contains("missing-token-or-document-id")
                .contains("DryRun")
                .contains("plannedSteps")
                .contains("check backend health")
                .contains("verify rag debug summary");
        assertThat(script)
                .doesNotContain("backendBaseUrl = $baseUrl")
                .doesNotContain("token = $Token")
                .doesNotContain("documentText")
                .doesNotContain("provider response")
                .doesNotContain("prompt =")
                .doesNotContain("finalAnswer =");
    }
}
