package com.docpilot.backend.memory.eval;

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
                "powershell",
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
                .contains("/api/memories/suggestions/extract")
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
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("apiKey =");
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
}
