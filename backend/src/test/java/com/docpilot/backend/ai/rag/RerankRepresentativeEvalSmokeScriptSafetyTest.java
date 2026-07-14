package com.docpilot.backend.ai.rag;

import com.docpilot.backend.testutil.PowerShellTestSupport;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RerankRepresentativeEvalSmokeScriptSafetyTest {

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
                .contains("Bounded real rerank representative eval smoke")
                .contains("12-case representative rerank eval")
                .contains("artifact redaction")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =");
    }

    @Test
    void shouldKeepRepresentativeEvalDelegatedAndSanitized() throws Exception {
        String wrapper = Files.readString(wrapperPath(), StandardCharsets.UTF_8);
        String delegate = Files.readString(delegatePath(), StandardCharsets.UTF_8);

        assertThat(wrapper)
                .contains("ValidateSet")
                .contains("dry-run")
                .contains("scripts/smoke/cloud-quality-smoke.ps1")
                .contains("docpilot-rerank-representative")
                .contains("-EnableRerankRepresentativeEvalGate")
                .contains("upliftCaseCount")
                .contains("citationLeakageCount")
                .contains("noEvidenceRegressionCount")
                .contains("artifact.json")
                .contains("ragRepresentativeEval")
                .contains("targetCoverageRegressionCount")
                .contains("targetRerankAppliedCaseCount")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =");
        assertThat(delegate)
                .contains("EnableRerankRepresentativeEvalGate")
                .contains("rerankRepresentativeEval")
                .contains("RR-EVAL-COMPLIANCE-TARGET")
                .contains("multiQueryEnabled = $true")
                .contains("Decode-Utf8Base64")
                .contains("application/json; charset=utf-8")
                .contains("UTF8.GetBytes")
                .contains("caseResults")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("apiKey =");
    }

    private static String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Path wrapperPath() {
        return Path.of("..", "scripts", "smoke", "rerank-representative-eval-smoke.ps1");
    }

    private static Path delegatePath() {
        return Path.of("..", "scripts", "smoke", "cloud-quality-smoke.ps1");
    }
}
