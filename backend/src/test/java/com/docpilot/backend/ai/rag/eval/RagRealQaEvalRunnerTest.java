package com.docpilot.backend.ai.rag.eval;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RagRealQaEvalRunnerTest {

    @Test
    void shouldEvaluateRealQaCasesOffline() throws Exception {
        RagRealQaEvalResult result = new RagRealQaEvalRunner().evaluateDefaultCases();

        assertThat(result.provider()).isEqualTo("in_memory");
        assertThat(result.embeddingProvider()).isEqualTo("mock");
        assertThat(result.metrics().caseCount()).isGreaterThanOrEqualTo(20);
        assertThat(result.metrics().casePassRate()).isEqualTo(1.0D);
        assertThat(result.metrics().answerCorrectnessRate()).isEqualTo(1.0D);
        assertThat(result.metrics().citationGroundingRate()).isEqualTo(1.0D);
        assertThat(result.metrics().noEvidencePrecision()).isEqualTo(1.0D);
        assertThat(result.metrics().forbiddenLeakRate()).isEqualTo(0.0D);
        assertThat(result.metrics().scopeViolationRate()).isEqualTo(0.0D);
        assertThat(result.metrics().rerankUpliftCandidateRate()).isGreaterThan(0.0D);
        assertThat(result.metrics().rerankUpliftCandidatePassRate()).isEqualTo(1.0D);
        assertThat(result.metrics().longDocumentCasePassRate()).isEqualTo(1.0D);
        assertThat(result.metrics().nearMissNoEvidenceRate()).isEqualTo(1.0D);
        assertThat(result.metrics().multiDocSummaryPassRate()).isEqualTo(1.0D);
        assertThat(result.metrics().distractorSuppressionRate()).isEqualTo(1.0D);
        assertThat(result.metrics().hardNegativePassRate()).isEqualTo(1.0D);
        assertThat(result.metrics().answerFaithfulnessPassRate()).isEqualTo(1.0D);
        assertThat(result.metrics().claimSupportPassRate()).isEqualTo(1.0D);
        assertThat(result.metrics().numericFaithfulnessPassRate()).isEqualTo(1.0D);
        assertThat(result.failedCaseIds()).isEmpty();
    }

    @Test
    void shouldWriteSanitizedRealQaEvalArtifact() throws Exception {
        RagRealQaEvalRunner runner = new RagRealQaEvalRunner();
        RagRealQaEvalResult result = runner.evaluateDefaultCases();
        Path reportPath = RagRealQaEvalRunner.DEFAULT_REPORT_PATH;

        runner.writeArtifact(result, reportPath);

        String report = Files.readString(reportPath, StandardCharsets.UTF_8);
        assertThat(report)
                .contains("casePassRate")
                .contains("answerCorrectnessRate")
                .contains("citationGroundingRate")
                .contains("rerankUpliftCandidateRate")
                .contains("rerankUpliftCandidatePassRate")
                .contains("longDocumentCasePassRate")
                .contains("nearMissNoEvidenceRate")
                .contains("multiDocSummaryPassRate")
                .contains("distractorSuppressionRate")
                .contains("hardNegativePassRate")
                .contains("answerFaithfulnessPassRate")
                .contains("claimSupportPassRate")
                .contains("numericFaithfulnessPassRate")
                .doesNotContain("DocPilot parse status marker")
                .doesNotContain("Security access policy overview")
                .doesNotContain("Which evidence")
                .doesNotContain("prompt")
                .doesNotContain("evidenceContext")
                .doesNotContain("Authorization")
                .doesNotContain("apiKey")
                .doesNotContain("secret");
    }
}
