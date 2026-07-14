package com.docpilot.backend.ai.agent;

import com.docpilot.backend.testutil.PowerShellTestSupport;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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
                .contains("New-DryRunSummary")
                .contains("secretRedactionEnabled")
                .contains("expectedDemoSteps")
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

    @Test
    void dryRunOutputShouldStayRedacted() throws Exception {
        Process process = new ProcessBuilder(
                PowerShellTestSupport.executable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                Path.of("scripts", "agent", "demo-agent-showcase.ps1").toString(),
                "-DryRun",
                "-BackendBaseUrl",
                "https://remote.example.invalid/private-demo")
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        String output = readAll(process.getInputStream());

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output)
                .contains("dry-run")
                .contains("backendReachable")
                .contains("unknown")
                .contains("authTokenPresent")
                .contains("documentIdPresent")
                .contains("secretRedactionEnabled")
                .contains("expectedDemoSteps")
                .contains("verify rag debug summary")
                .doesNotContain("https://")
                .doesNotContain("remote.example.invalid")
                .doesNotContain("remote-redacted")
                .doesNotContain("backendLocation")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("API key")
                .doesNotContain("apiKey")
                .doesNotContain("baseUrl")
                .doesNotContain("endpoint")
                .doesNotContain("prompt")
                .doesNotContain("document content")
                .doesNotContain("documentText")
                .doesNotContain("provider response");
    }

    private static String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
