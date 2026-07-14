package com.docpilot.backend.document.parser;

import com.docpilot.backend.testutil.PowerShellTestSupport;

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
                .contains("wait direct retrieve until vector search is visible with the same user-style question used by QA")
                .contains("PDF")
                .contains("HTML")
                .contains("DOCX")
                .contains("LONG_MD")
                .contains("temporary smoke users so LONG_MD does not trip the real upload rate limit")
                .contains("directRetrieveHit")
                .contains("qaRetrievalHit")
                .contains("expectedStructures")
                .contains("structureSignals")
                .contains("HTML noise-isolation, multi-chunk, and long markdown embedding batch-split coverage")
                .contains("long markdown embedding batch-split coverage")
                .contains("expectedMinChunks")
                .contains("multiChunkVerified")
                .contains("indexedChunkCount")
                .contains("vectorIdCount")
                .contains("qdrantPointCount")
                .contains("mysqlQdrantParity")
                .contains("directRetrieveDiagnostic")
                .contains("qaRetrieveDiagnostic")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("api" + "Key =");
    }

    @Test
    void shouldDryRunWithoutReadingEnvOrCreatingData() throws Exception {
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
                .contains("\"mode\":")
                .contains("\"dry-run\"")
                .contains("PDF")
                .contains("HTML")
                .contains("DOCX")
                .contains("LONG_MD")
                .contains("parserQualityReport")
                .contains("fixtureStructureCoverage")
                .contains("indexParitySummary")
                .contains("HTML noise isolation, multi-chunk coverage, and long markdown batch-split coverage")
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
                .contains("$caseToken = if (-not [string]::IsNullOrWhiteSpace([string]$followUp.token))")
                .contains("New-RagCallDiagnostic")
                .contains("To-SafeArray")
                .contains("$hits = if ($retrieve.ok) { To-SafeArray $retrieve.data.hits } else { @() }")
                .contains("Get-SafeItemCount")
                .contains("Confirm-EnvironmentStability")
                .contains("environment_unstable")
                .contains("Get-FixtureStructureSignals")
                .contains("html_noise_excluded")
                .contains("html_multi_chunk")
                .contains("multi_chunk_source_coverage_missing")
                .contains("embedding_batch_split_candidate")
                .contains("Long Markdown Parser Smoke")
                .contains("long-batch-marker")
                .contains("parserl_")
                .contains("temporary smoke users registered")
                .contains("Get-IndexParitySummary")
                .contains("mysql_qdrant_parity_missing")
                .contains("mysql/qdrant index parity")
                .contains("payloadSummaryOkCount")
                .contains("locatorPayloadCount")
                .contains("Related sidebar noise")
                .contains("fixture_structure_missing")
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
