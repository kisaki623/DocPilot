package com.docpilot.backend.conversation;

import com.docpilot.backend.testutil.PowerShellTestSupport;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationGroundingSmokeScriptSafetyTest {

    @Test
    void shouldPrintPlanWithoutReadingEnvOrCreatingData() throws Exception {
        Process process = new ProcessBuilder(
                PowerShellTestSupport.executable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                scriptPath().toString(),
                "-Mode",
                "plan")
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        String output = readAll(process.getInputStream());

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output)
                .contains("docpilot-conversation-grounding")
                .contains("no-kb-model-only")
                .contains("no-kb-strict-normalized")
                .contains("auto-generic-no-rag")
                .contains("auto-no-evidence-fallback-model")
                .contains("strict-no-evidence-refusal")
                .contains("auto-rag-evidence-citations")
                .contains("modelSkipped")
                .contains("artifact redaction")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =")
                .doesNotContain("MYSQL_PASSWORD");
    }

    @Test
    void shouldPrintDryRunWithoutReadingEnvOrCreatingData() throws Exception {
        Process process = new ProcessBuilder(
                PowerShellTestSupport.executable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                scriptPath().toString(),
                "-Mode",
                "dry-run")
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        String output = readAll(process.getInputStream());

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output)
                .contains("\"mode\":  \"dry-run\"")
                .contains("migrationScriptExists")
                .contains("noDataCreated")
                .contains("strict-no-evidence-refusal")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =")
                .doesNotContain("MYSQL_PASSWORD");
    }

    @Test
    void shouldKeepRunArtifactSanitizedAndBounded() throws Exception {
        String script = Files.readString(scriptPath(), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("ValidateSet")
                .contains("dry-run")
                .contains("backend/target/conversation-grounding")
                .contains("008_add_context_trace_grounding.sql")
                .contains("groundingPolicy")
                .contains("routeDecision")
                .contains("llmCalled")
                .contains("modelSkipped")
                .contains("AUTO_NO_EVIDENCE_MODEL")
                .contains("STRICT_NO_EVIDENCE_FALLBACK")
                .contains("AUTO_RAG_EVIDENCE")
                .contains("artifactPolicy")
                .contains("no token, password, raw prompt, raw answer, raw evidence")
                .contains("Stop-StartedProcesses")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("Write-Host $token")
                .doesNotContain("Write-Output $token")
                .doesNotContain("Write-Host $password")
                .doesNotContain("Write-Output $password")
                .doesNotContain("Write-Host $EnvFile")
                .doesNotContain("Write-Output $EnvFile")
                .doesNotContain("apiKey =");
    }

    private static String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Path scriptPath() {
        return Path.of("..", "scripts", "smoke", "conversation-grounding-smoke.ps1");
    }
}
