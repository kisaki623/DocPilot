package com.docpilot.backend.ai.agent;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSearchRouteSmokeScriptSafetyTest {

    @Test
    void shouldPrintPlanWithoutReadingEnvOrCreatingData() throws Exception {
        Process process = new ProcessBuilder(
                "powershell",
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
                .contains("Agent search route smoke plan only")
                .contains("retrieval-only task")
                .contains("grounded answer task")
                .contains("document_search_tool")
                .contains("rag_qa_tool")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("api" + "Key =");
    }

    @Test
    void shouldDryRunWithoutReadingEnvOrCreatingData() throws Exception {
        Process process = new ProcessBuilder(
                "powershell",
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
                .contains("\"overallStatus\":")
                .contains("\"PASS\"")
                .contains("runnerTestExists")
                .contains("artifactRootUnderBackendTarget")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("api" + "Key =");
    }

    @Test
    void shouldKeepScriptSanitized() throws Exception {
        String script = Files.readString(scriptPath(), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("ValidateSet")
                .contains("dry-run")
                .contains("AgentSearchRouteSmokeTest")
                .contains("backend/target/agent-search-route")
                .contains("DOCPILOT_AGENT_SEARCH_ROUTE_SMOKE_ARTIFACT")
                .doesNotContain("Get-Content -LiteralPath $EnvFile")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("api" + "Key =")
                .doesNotContain("Remove-Item -Recurse");
    }

    private Path scriptPath() {
        return Path.of("..", "scripts", "smoke", "agent-search-route-smoke.ps1").normalize();
    }

    private String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
