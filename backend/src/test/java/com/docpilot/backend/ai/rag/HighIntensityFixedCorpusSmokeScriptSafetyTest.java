package com.docpilot.backend.ai.rag;

import com.docpilot.backend.testutil.PowerShellTestSupport;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HighIntensityFixedCorpusSmokeScriptSafetyTest {

    @Test
    void shouldPrintPlanWithoutReadingEnvOrCreatingData() throws Exception {
        Process process = new ProcessBuilder(
                PowerShellTestSupport.executable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                wrapperPath().toString(),
                "-Mode",
                "plan")
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        String output = readAll(process.getInputStream());

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output)
                .contains("docpilot-high-intensity-fixed-corpus")
                .contains("CONTRACT_ALPHA")
                .contains("SLA_BETA")
                .contains("API_POLICY")
                .contains("INCIDENT_REVIEW")
                .contains("DECOY_DRAFT")
                .contains("PROMPT_INJECTION")
                .contains("KB_CORE")
                .contains("KB_NOISY")
                .contains("T02_serial_duplicate_upload")
                .contains("T06_contract_precise_numbers")
                .contains("T15_prompt_injection")
                .contains("fixedBusinessCorpus")
                .contains("artifactPolicy")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =")
                .doesNotContain("MYSQL_PASSWORD");
    }

    @Test
    void shouldKeepWrapperDelegatedAndSanitized() throws Exception {
        String wrapper = Files.readString(wrapperPath(), StandardCharsets.UTF_8);

        assertThat(wrapper)
                .contains("ValidateSet")
                .contains("dry-run")
                .contains("scripts/smoke/cloud-quality-smoke.ps1")
                .contains("docpilot-high-intensity-fixed-corpus")
                .contains("backend/target/high-intensity-acceptance")
                .contains("-EnableFixedBusinessCorpusGate")
                .contains("T02_serial_duplicate_upload")
                .contains("T06_contract_precise_numbers")
                .contains("T15_prompt_injection")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("apiKey =")
                .doesNotContain("Write-Host $EnvFile")
                .doesNotContain("Write-Output $EnvFile");
    }

    @Test
    void shouldKeepDelegateFixedCorpusGateBoundedAndRedacted() throws Exception {
        String delegate = Files.readString(delegatePath(), StandardCharsets.UTF_8);

        assertThat(delegate)
                .contains("EnableFixedBusinessCorpusGate")
                .contains("Invoke-FixedBusinessCorpusGate")
                .contains("New-FixedCorpusDefinitions")
                .contains("New-FixedCorpusCaseDefinitions")
                .contains("Test-FixedCorpusArtifactShape")
                .contains("T02_serial_duplicate_upload")
                .contains("T06_contract_precise_numbers")
                .contains("T15_prompt_injection")
                .contains("CONTRACT_ALPHA")
                .contains("PROMPT_INJECTION")
                .contains("ORANGE-47")
                .contains("\"question\"")
                .contains("\"answer\"")
                .contains("\"snippet\"")
                .contains("\"quoteText\"")
                .contains("fixedBusinessCorpusGateEnabled")
                .contains("fixedBusinessCorpusGate")
                .contains("application/json; charset=utf-8")
                .contains("UTF8.GetBytes")
                .doesNotContain("rawAnswer =")
                .doesNotContain("rawResponse =")
                .doesNotContain("prompt =")
                .doesNotContain("evidenceContext =")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("apiKey =");
    }

    @Test
    void shouldPrintDryRunContractThroughDelegate() throws Exception {
        ProcessResult plan = runPowerShell(List.of(
                "-File",
                delegatePath().toString(),
                "-Mode",
                "plan",
                "-EnableFixedBusinessCorpusGate"), 20);
        ProcessResult dryRun = runPowerShell(List.of(
                "-File",
                delegatePath().toString(),
                "-Mode",
                "dry-run",
                "-ArtifactRoot",
                "backend/target/high-intensity-acceptance",
                "-EnableFixedBusinessCorpusGate"), 30);

        assertThat(plan.completed()).isTrue();
        assertThat(plan.exitCode()).isZero();
        assertThat(plan.output())
                .contains("fixedBusinessCorpusGate")
                .contains("CONTRACT_ALPHA")
                .contains("T02_serial_duplicate_upload")
                .contains("T15_prompt_injection")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =");
        assertThat(dryRun.completed()).isTrue();
        assertThat(dryRun.exitCode()).isZero();
        assertThat(dryRun.output())
                .contains("\"mode\":  \"dry-run\"")
                .contains("fixedBusinessCorpusPlanContract")
                .contains("CONTRACT_ALPHA")
                .contains("T02_serial_duplicate_upload")
                .contains("T15_prompt_injection")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =");
    }

    private static String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static ProcessResult runPowerShell(List<String> arguments, long timeoutSeconds) throws Exception {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command().add(PowerShellTestSupport.executable());
        builder.command().add("-NoProfile");
        builder.command().add("-ExecutionPolicy");
        builder.command().add("Bypass");
        builder.command().addAll(arguments);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try {
                return readAll(process.getInputStream());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
        }
        String text = output.get(10, TimeUnit.SECONDS);
        int exitCode = completed ? process.exitValue() : -1;
        return new ProcessResult(completed, exitCode, text);
    }

    private record ProcessResult(boolean completed, int exitCode, String output) {
    }

    private static Path wrapperPath() {
        return Path.of("..", "scripts", "smoke", "high-intensity-fixed-corpus-smoke.ps1");
    }

    private static Path delegatePath() {
        return Path.of("..", "scripts", "smoke", "cloud-quality-smoke.ps1");
    }
}
