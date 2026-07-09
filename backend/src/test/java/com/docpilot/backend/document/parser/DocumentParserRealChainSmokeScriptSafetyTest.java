package com.docpilot.backend.document.parser;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserRealChainSmokeScriptSafetyTest {

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
                .contains("wait direct retrieve until vector search is visible with the same user-style question used by QA")
                .contains("PDF")
                .contains("HTML")
                .contains("DOCX")
                .contains("directRetrieveHit")
                .contains("qaRetrievalHit")
                .contains("directRetrieveDiagnostic")
                .contains("qaRetrieveDiagnostic")
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
                .contains("\"mode\":")
                .contains("\"dry-run\"")
                .contains("PDF")
                .contains("HTML")
                .contains("DOCX")
                .contains("parserQualityReport")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("api" + "Key =");
    }

    @Test
    void shouldUseQaStyleQuestionForDirectRetrieveAndKeepArtifactRedacted() throws Exception {
        String script = Files.readString(scriptPath(), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("$question = \"请根据文档回答 $($case.query)\"")
                .contains("query = $question")
                .contains("$attempt -le 15")
                .contains("DirectRetrieveFollowUps")
                .contains("Confirm-DirectRetrieveFollowUps $token")
                .contains("New-RagCallDiagnostic")
                .contains("To-SafeArray")
                .contains("Get-SafeItemCount")
                .contains("Confirm-EnvironmentStability")
                .contains("environment_unstable")
                .contains("collectionPresent")
                .contains("forbiddenArtifactFields")
                .contains("prompt")
                .contains("answer")
                .contains("evidence context")
                .doesNotContain("query = $case.query; topK = 5")
                .doesNotContain("directRetrieveQuestion")
                .doesNotContain("Remove-Item -Recurse");
    }

    private Path scriptPath() {
        return Path.of("..", "scripts", "smoke", "document-parser-real-chain-smoke.ps1").normalize();
    }

    private String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
