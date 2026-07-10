package com.docpilot.backend.ai.agent;

import com.docpilot.backend.testutil.PowerShellTestSupport;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineAgentRagDemoSuiteScriptSafetyTest {

    @Test
    void shouldAggregateExistingOfflineScriptsWithoutNetworkFields() throws Exception {
        String script = Files.readString(Path.of("scripts", "agent", "run-offline-agent-rag-demo-suite.ps1"),
                StandardCharsets.UTF_8);

        assertThat(script)
                .contains("demo-agent-showcase.ps1")
                .contains("run-rag-vector-store-offline-demo.ps1")
                .contains("run-rag-retrieval-eval.ps1")
                .contains("run-rag-evaluation-artifact.ps1")
                .contains("run-rag-qa-trace-smoke.ps1")
                .contains("show-rag-eval-trend.ps1")
                .contains("function Get-PowerShellExecutable")
                .contains("$env:OS -eq \"Windows_NT\"")
                .contains("& $powerShellExecutable @commandArguments")
                .contains("offline-agent-rag-demo-suite")
                .contains("embeddingProvider")
                .contains("fake")
                .contains("vectorStore")
                .contains("in_memory")
                .contains("qdrantEnabled")
                .contains("providerHttp");
        assertThat(script)
                .doesNotContain("Invoke-RestMethod")
                .doesNotContain("Invoke-WebRequest")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("apiKey")
                .doesNotContain("providerResponse")
                .doesNotContain("documentText")
                .doesNotContain("prompt =")
                .doesNotContain("& powershell @commandArguments");
    }

    @Test
    void skipTestsOutputShouldStayOfflineAndSanitized() throws Exception {
        Path outputPath = Path.of("target", "rag-demo", "offline-agent-rag-demo-suite-test-summary.json");
        Files.deleteIfExists(outputPath);

        Process process = new ProcessBuilder(
                PowerShellTestSupport.executable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                Path.of("scripts", "agent", "run-offline-agent-rag-demo-suite.ps1").toString(),
                "-SkipTests",
                "-OutputPath",
                outputPath.toString())
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        String output = readAll(process.getInputStream());

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output)
                .contains("offline-agent-rag-demo-suite")
                .contains("\"mode\":")
                .contains("offline")
                .contains("embeddingProvider")
                .contains("fake")
                .contains("vectorStore")
                .contains("in_memory")
                .contains("qdrantEnabled")
                .contains("false")
                .contains("providerHttp")
                .contains("checks")
                .contains("artifactPaths")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("apiKey")
                .doesNotContain("baseUrl")
                .doesNotContain("endpoint")
                .doesNotContain("prompt")
                .doesNotContain("documentText")
                .doesNotContain("providerResponse");
        assertThat(Files.exists(outputPath)).isTrue();
    }

    private static String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
