package com.docpilot.backend.memory.eval;

import com.docpilot.backend.testutil.PowerShellTestSupport;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryQualitySmokeScriptSafetyTest {

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
                .contains("docpilot-memory-quality")
                .contains("T31 active memory enters AGENT_MEMORY trace before delete")
                .contains("T31 RECENT_TURNS contextMode suppresses long-term memory")
                .contains("T31 deleted memory is soft-deleted and no longer selected")
                .contains("T29 Agent Memory candidate requires user confirmation")
                .contains("T30 sensitive memory candidate is rejected")
                .contains("accepted suggestion becomes ACTIVE")
                .contains("ignored suggestion stays out of active memory list")
                .contains("conflicting answer-style suggestion reports governance hint")
                .contains("conflicting suggestion accept is blocked before ACTIVE")
                .contains("conflicting suggestion can be kept, replaced, and merged")
                .contains("active memory edit succeeds while sensitive edit is blocked")
                .contains("userMemory")
                .contains("ragEvidence")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =");
    }

    @Test
    void shouldKeepWrapperDelegatedAndSanitized() throws Exception {
        String wrapper = Files.readString(wrapperPath(), StandardCharsets.UTF_8);
        String delegate = Files.readString(delegatePath(), StandardCharsets.UTF_8);

        assertThat(wrapper)
                .contains("ValidateSet")
                .contains("dry-run")
                .contains("scripts/smoke/cloud-quality-smoke.ps1")
                .contains("docpilot-memory-quality")
                .contains("backend/target/memory-quality")
                .contains("-EnableMemoryQualityGate")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("apiKey =");
        assertThat(delegate)
                .contains("EnableMemoryQualityGate")
                .contains("memoryQuality")
                .contains("Get-ActiveMemoryCountByUser")
                .contains("Get-MemoryStatusUseCount")
                .contains("T31-memory-delete-disable-lifecycle")
                .contains("/api/memories/$t31MemoryId")
                .contains("strictMemoryDisableApiNotImplemented")
                .contains("postDeleteUseCountDelta")
                .contains("/api/memories/suggestions/extract")
                .contains("T29-agent-memory-candidate-confirmation")
                .contains("T30-sensitive-memory-rejection")
                .contains("Get-MemoryRowCountBySourceConversation")
                .contains("candidateCountFromT30")
                .contains("activeMemoryContainsIgnored")
                .contains("conflict_active_memory")
                .contains("conflictWithId")
                .contains("conflictAcceptBlocked")
                .contains("/api/memories/suggestions/$($conflictingSuggestion.memoryId)/resolve")
                .contains("KEEP_ACTIVE")
                .contains("REPLACE_ACTIVE")
                .contains("MERGE_WITH_ACTIVE")
                .contains("sensitiveEditBlocked")
                .contains("memory suggestion requires governance before accept")
                .doesNotContain("-e $query")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("s" + "k" + "-test-secret-123456")
                .doesNotContain("rawPrompt")
                .doesNotContain("rawAnswer")
                .doesNotContain("apiKey =");
    }

    @Test
    void shouldKeepMemoryProviderWrapperGatedAndSanitized() throws Exception {
        Process process = new ProcessBuilder(
                PowerShellTestSupport.executable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                providerWrapperPath().toString(),
                "-Mode",
                "plan")
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        String output = readAll(process.getInputStream());
        String wrapper = Files.readString(providerWrapperPath(), StandardCharsets.UTF_8);

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output)
                .contains("docpilot-memory-provider")
                .contains("MemoryProviderExtractionRealProviderSmokeTest")
                .contains("JSON-only memory suggestion contract")
                .contains("RAG evidence isolation")
                .contains("secret-like content rejection")
                .contains("Chinese durable PREFERENCE and PROJECT_STATE extraction")
                .contains("one-time instruction suppression")
                .contains("\"maxModelCalls\":  6")
                .contains("redacted artifact")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =");

        assertThat(wrapper)
                .contains("ValidateSet")
                .contains("dry-run")
                .contains("EnvFile")
                .contains("Import-EnvFileIfPresent")
                .contains("env_file_outside_repo")
                .contains("DOCPILOT_MEMORY_PROVIDER_SMOKE_ENABLED")
                .contains("DOCPILOT_MEMORY_PROVIDER_SMOKE_ARTIFACT")
                .contains("AI_REAL_PROVIDER")
                .contains("AI_REAL_BASE_URL")
                .contains("AI_REAL_API_KEY")
                .contains("AI_REAL_MODEL")
                .contains("MemoryProviderExtractionRealProviderSmokeTest")
                .contains("backend/target/memory-provider")
                .contains("$FixedSuiteCaseCount = 6")
                .contains("Test-SafeArtifactRoot")
                .contains("Test-SafeSmokePrefix")
                .contains("smoke_prefix_invalid")
                .contains("artifact_summary_invalid")
                .contains("maven_test_failed")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("maven.log")
                .doesNotContain("Get-Content -LiteralPath $EnvFile")
                .doesNotContain("Write-Host $EnvFile")
                .doesNotContain("Write-Output $EnvFile")
                .doesNotContain("Write-Host $apiKey")
                .doesNotContain("Write-Output $apiKey");
    }

    private static String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Path wrapperPath() {
        return Path.of("..", "scripts", "smoke", "memory-quality-smoke.ps1");
    }

    private static Path delegatePath() {
        return Path.of("..", "scripts", "smoke", "cloud-quality-smoke.ps1");
    }

    private static Path providerWrapperPath() {
        return Path.of("..", "scripts", "smoke", "memory-provider-extraction-smoke.ps1");
    }
}
