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
                .contains("hard_negative")
                .contains("answer_faithfulness")
                .contains("claim_support")
                .contains("numeric_faithfulness")
                .contains("real_provider_faithfulness")
                .contains("frontend_interaction")
                .contains("representativeCorpus")
                .contains("multi_query")
                .contains("multiQueryRag")
                .contains("answerGrounding")
                .contains("realQaHardGate")
                .contains("realQaSemanticGate")
                .contains("realProviderFaithfulness")
                .contains("frontendInteraction")
                .contains("representativeCorpusEnabledByDefault")
                .contains("multiQueryGateEnabledByDefault")
                .contains("realQaHardGateEnabledByDefault")
                .contains("realQaSemanticGateEnabledByDefault")
                .contains("realProviderFaithfulnessGateEnabledByDefault")
                .contains("frontendInteractionGateEnabledByDefault")
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
                .contains("SkipMultiQueryGate")
                .contains("SkipRealQaHardGate")
                .contains("SkipRealQaSemanticGate")
                .contains("SkipRealProviderFaithfulnessGate")
                .contains("SkipFrontendInteractionGate")
                .contains("EnableRepresentativeCorpusGate")
                .contains("EnableMultiQueryGate")
                .contains("EnableRealQaHardGate")
                .contains("EnableRealQaSemanticGate")
                .contains("EnableRealProviderFaithfulnessGate")
                .contains("EnableFrontendInteractionGate")
                .contains("answer_grounding")
                .contains("multi_query")
                .contains("frontend_interaction")
                .contains("answerGrounding")
                .contains("realQaHardGate")
                .contains("realQaSemanticGate")
                .contains("realProviderFaithfulness")
                .contains("frontendInteraction")
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
                .contains("realQaHardGate")
                .contains("realQaSemanticGate")
                .contains("realProviderFaithfulness")
                .contains("multiQueryRag")
                .contains("frontendInteraction")
                .contains("EnableFrontendInteractionGate")
                .contains("frontendInteractionGateEnabled")
                .contains("frontendInteractionGate")
                .contains("singleDocumentChineseShortRetrieve")
                .contains("singleDocumentNumericShortRetrieve")
                .contains("knowledgeBaseSimilarShortInterference")
                .contains("failureBuckets")
                .contains("multiQueryApplied")
                .contains("queryVariantCount")
                .contains("Test-RealAnswerProvider")
                .contains("hardNegative")
                .contains("answerFaithfulness")
                .contains("claimSupport")
                .contains("numericFaithfulness")
                .doesNotContain("answerText =")
                .doesNotContain("rawAnswer =")
                .doesNotContain("rawResponse =")
                .doesNotContain("prompt =")
                .doesNotContain("evidenceContext =")
                .doesNotContain("token = $token")
                .doesNotContain("uiToken =");
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
