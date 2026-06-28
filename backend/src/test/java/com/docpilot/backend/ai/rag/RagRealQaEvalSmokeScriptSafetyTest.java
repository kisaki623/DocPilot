package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RagRealQaEvalSmokeScriptSafetyTest {

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
                .contains("docpilot-rag-real-qa")
                .contains("factual_lookup")
                .contains("no_evidence")
                .contains("representative_corpus")
                .contains("answer_grounding")
                .contains("representativeCorpus")
                .contains("answerGrounding")
                .contains("representativeCorpusEnabledByDefault")
                .contains("artifactRedaction")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =");
    }

    @Test
    void shouldKeepWrapperDelegatedAndSanitized() throws Exception {
        String script = Files.readString(scriptPath(),
                StandardCharsets.UTF_8);

        assertThat(script)
                .contains("ValidateSet")
                .contains("dry-run")
                .contains("scripts/smoke/cloud-quality-smoke.ps1")
                .contains("docpilot-rag-real-qa")
                .contains("backend/target/rag-real-qa")
                .contains("QualityMinSimilarityThreshold")
                .contains("SkipRepresentativeCorpusGate")
                .contains("EnableRepresentativeCorpusGate")
                .contains("answer_grounding")
                .contains("answerGrounding")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("apiKey =")
                .doesNotContain("Write-Host $EnvFile")
                .doesNotContain("Write-Output $EnvFile");
    }

    @Test
    void shouldKeepAnswerGroundingArtifactSanitized() throws Exception {
        String script = Files.readString(delegateScriptPath(), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("Test-AnswerGrounding")
                .contains("answerGrounding")
                .contains("expectedMarkerHits")
                .contains("forbiddenMarkerHit")
                .contains("citationMarkerPresent")
                .doesNotContain("answerText =")
                .doesNotContain("rawAnswer =")
                .doesNotContain("rawResponse =")
                .doesNotContain("prompt =")
                .doesNotContain("evidenceContext =");
    }

    private static String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Path scriptPath() {
        return Path.of("..", "scripts", "smoke", "rag-real-qa-eval-smoke.ps1");
    }

    private static Path delegateScriptPath() {
        return Path.of("..", "scripts", "smoke", "cloud-quality-smoke.ps1");
    }
}
