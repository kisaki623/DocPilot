package com.docpilot.backend.ai.rag;

import com.docpilot.backend.testutil.PowerShellTestSupport;

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
                .contains("Test-TextContainsAll")
                .contains("Test-TextContainsAny")
                .contains("answerGrounding")
                .contains("expectedMarkerHits")
                .contains("forbiddenMarkerHit")
                .contains("citationMarkerPresent")
                .contains("naturalCorpus")
                .contains("EnableNaturalCorpusGate")
                .contains("naturalCorpusGateEnabled")
                .contains("naturalCorpusGate")
                .contains("targetRetrieveCoverage")
                .contains("targetCitationCoverage")
                .contains("citationPhraseSupport")
                .contains("answerFaithfulnessRequired")
                .contains("answerFaithfulnessPassCount")
                .contains("citationPhraseSupportPassCount")
                .contains("evidenceCoverageReport")
                .contains("retrievalCoverageMisses")
                .contains("citationCoverageMisses")
                .contains("citationPhraseMisses")
                .contains("answerFaithfulnessMisses")
                .contains("distractorCitationLeaks")
                .contains("noEvidenceFailures")
                .contains("distractorCitation")
                .contains("noEvidenceCorrect")
                .contains("conversationTraceCoverage")
                .contains("schemaVersion = 2")
                .contains("caseResults")
                .contains("casePassRate")
                .contains("targetRetrieveCoverage")
                .contains("targetCitationCoverage")
                .contains("distractorCitation")
                .contains("natural_approval_chain")
                .contains("natural_date_fact")
                .contains("natural_negative_fact")
                .contains("finance-expense-approval")
                .contains("governance-hotfix-retention")
                .contains("realQaHardGate")
                .contains("realQaSemanticGate")
                .contains("realProviderFaithfulness")
                .contains("multiQueryRag")
                .contains("frontendInteraction")
                .contains("EnableKnowledgeBaseAgentGate")
                .contains("knowledgeBaseAgent")
                .contains("knowledgeBaseAgentGateEnabled")
                .contains("knowledgeBaseAgentGate")
                .contains("EnableFrontendInteractionGate")
                .contains("frontendInteractionGateEnabled")
                .contains("frontendInteractionGate")
                .contains("nodeOverallStatus")
                .contains("nodeSafeMessage")
                .contains("scriptExecution")
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

    @Test
    void shouldKeepNaturalCorpusWrapperDelegatedAndSanitized() throws Exception {
        Process process = new ProcessBuilder(
                PowerShellTestSupport.executable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                naturalCorpusScriptPath().toString(),
                "-Mode",
                "plan")
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        String output = readAll(process.getInputStream());
        String script = Files.readString(naturalCorpusScriptPath(), StandardCharsets.UTF_8);

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output)
                .contains("docpilot-rag-natural-corpus")
                .contains("naturalCorpusEnabledByDefault")
                .contains("schemaVersion")
                .contains("defaultCorpusTarget")
                .contains("defaultDocumentTarget")
                .contains("defaultCaseTarget")
                .contains("multiQueryGateEnabledByDefault")
                .contains("frontendInteractionGateEnabledByDefault")
                .contains("natural_single_doc_fact")
                .contains("natural_numeric_fact")
                .contains("natural_multi_doc_summary")
                .contains("natural_distractor_control")
                .contains("natural_no_evidence")
                .contains("natural_date_fact")
                .contains("natural_approval_chain")
                .contains("natural_negative_fact")
                .contains("natural_case_coverage")
                .contains("natural_conversation_trace")
                .contains("naturalCorpus")
                .contains("artifactRedaction")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =");

        assertThat(script)
                .contains("scripts/smoke/cloud-quality-smoke.ps1")
                .contains("backend/target/rag-natural-corpus")
                .contains("SkipNaturalCorpusGate")
                .contains("EnableNaturalCorpusGate")
                .contains("EnableMultiQueryGate")
                .contains("EnableFrontendInteractionGate")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("apiKey =")
                .doesNotContain("Write-Host $EnvFile")
                .doesNotContain("Write-Output $EnvFile");
    }

    @Test
    void shouldKeepRealUserQaAuditWrapperDelegatedAndSanitized() throws Exception {
        Process process = new ProcessBuilder(
                PowerShellTestSupport.executable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                realUserQaAuditScriptPath().toString(),
                "-Mode",
                "plan")
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        String output = readAll(process.getInputStream());
        String script = Files.readString(realUserQaAuditScriptPath(), StandardCharsets.UTF_8);

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output)
                .contains("docpilot-real-user-qa")
                .contains("backend/target/audit")
                .contains("naturalCorpusEnabledByDefault")
                .contains("multiQueryGateEnabledByDefault")
                .contains("frontendInteractionGateEnabledByDefault")
                .contains("memoryQualityGateEnabledByDefault")
                .contains("single-document RAG answer has grounded citation")
                .contains("KnowledgeBase multi-document RAG covers both target documents")
                .contains("quote-first citation UI")
                .contains("ACTIVE user memory and RAG evidence stay separated")
                .contains("permission isolation")
                .contains("artifactRedaction")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("apiKey =");

        assertThat(script)
                .contains("ValidateSet")
                .contains("dry-run")
                .contains("scripts/smoke/cloud-quality-smoke.ps1")
                .contains("docpilot-real-user-qa")
                .contains("backend/target/audit")
                .contains("SkipNaturalCorpusGate")
                .contains("SkipMultiQueryGate")
                .contains("SkipFrontendInteractionGate")
                .contains("SkipMemoryQualityGate")
                .contains("EnableNaturalCorpusGate")
                .contains("EnableMultiQueryGate")
                .contains("EnableFrontendInteractionGate")
                .contains("EnableMemoryQualityGate")
                .contains("naturalCorpus")
                .contains("multiQueryRag")
                .contains("frontendInteraction")
                .contains("memoryQuality")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("apiKey =")
                .doesNotContain("Write-Host $EnvFile")
                .doesNotContain("Write-Output $EnvFile");
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

    private static Path naturalCorpusScriptPath() {
        return Path.of("..", "scripts", "smoke", "rag-natural-corpus-audit-smoke.ps1");
    }

    private static Path realUserQaAuditScriptPath() {
        return Path.of("..", "scripts", "smoke", "real-user-qa-experience-audit.ps1");
    }
}
