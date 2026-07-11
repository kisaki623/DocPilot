package com.docpilot.backend.ai.rag;

import com.docpilot.backend.testutil.PowerShellTestSupport;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RerankEffectSmokeScriptSafetyTest {

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
                .contains("Small real rerank effect smoke")
                .contains("hard fixture target/distractor comparison")
                .contains("artifact redaction")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =");
    }

    @Test
    void shouldKeepHardFixtureDelegatedAndSanitized() throws Exception {
        String wrapper = Files.readString(wrapperPath(), StandardCharsets.UTF_8);
        String delegate = Files.readString(delegatePath(), StandardCharsets.UTF_8);

        assertThat(wrapper)
                .contains("ValidateSet")
                .contains("dry-run")
                .contains("scripts/smoke/cloud-quality-smoke.ps1")
                .contains("docpilot-rerank-effect")
                .contains("-EnableRerankHardGate")
                .contains("hardUpliftObserved")
                .contains("rerankFailureReason")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("apiKey =");
        assertThat(delegate)
                .contains("EnableRerankHardGate")
                .contains("rerankHardFixture")
                .contains("HARD-RERANK-FORBIDDEN")
                .contains("targetBestRank")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("apiKey =");
    }

    private static String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Path wrapperPath() {
        return Path.of("..", "scripts", "smoke", "rerank-effect-smoke.ps1");
    }

    private static Path delegatePath() {
        return Path.of("..", "scripts", "smoke", "cloud-quality-smoke.ps1");
    }
}
