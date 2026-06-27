package com.docpilot.backend.ai.rag.eval;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseRagEvalRunnerTest {

    @Test
    void shouldEvaluateKnowledgeBaseRagOfflineWithInMemoryStore() throws Exception {
        KnowledgeBaseRagEvalRunner runner = new KnowledgeBaseRagEvalRunner();

        KnowledgeBaseRagEvalResult result = runner.evaluateDefaultCases();

        assertThat(result.provider()).isEqualTo("in_memory");
        assertThat(result.embeddingProvider()).isEqualTo("mock");
        assertThat(result.metrics().caseCount()).isGreaterThanOrEqualTo(5);
        assertThat(result.metrics().hitAtK()).isEqualTo(1.0D);
        assertThat(result.metrics().documentHitRate()).isEqualTo(1.0D);
        assertThat(result.metrics().citationHitRate()).isEqualTo(1.0D);
        assertThat(result.metrics().answerHitRate()).isEqualTo(1.0D);
        assertThat(result.metrics().citationCountRate()).isEqualTo(1.0D);
        assertThat(result.metrics().multiDocumentCoverageRate()).isEqualTo(1.0D);
        assertThat(result.metrics().groundedAnswerRate()).isEqualTo(1.0D);
        assertThat(result.metrics().forbiddenAnswerLeakRate()).isEqualTo(0.0D);
        assertThat(result.metrics().noEvidenceRate()).isEqualTo(1.0D);
        assertThat(result.metrics().noEvidenceCitationFreeRate()).isEqualTo(1.0D);
        assertThat(result.metrics().scopeViolationRate()).isEqualTo(0.0D);
        assertThat(result.noEvidenceModelCallCount()).isZero();
        assertThat(result.failedCaseIds()).isEmpty();
        assertThat(result.caseEvaluations()).allSatisfy(evaluation -> assertThat(evaluation.passed()).isTrue());
        assertThat(result.caseEvaluations())
                .anySatisfy(evaluation -> assertThat(evaluation.citationDocumentIds()).contains(3201L, 3202L))
                .anySatisfy(evaluation -> {
                    assertThat(evaluation.multiDocumentCoverageRequired()).isTrue();
                    assertThat(evaluation.multiDocumentCoverageHit()).isTrue();
                })
                .anySatisfy(evaluation -> assertThat(evaluation.noEvidenceHit()).isTrue());
    }

    @Test
    void shouldWriteSanitizedKnowledgeBaseRagEvalArtifact() throws Exception {
        KnowledgeBaseRagEvalRunner runner = new KnowledgeBaseRagEvalRunner();
        KnowledgeBaseRagEvalResult result = runner.evaluateDefaultCases();
        Path reportPath = KnowledgeBaseRagEvalRunner.DEFAULT_REPORT_PATH;

        runner.writeArtifact(result, reportPath);

        String report = Files.readString(reportPath, StandardCharsets.UTF_8);
        assertThat(report)
                .contains("\"provider\" : \"in_memory\"")
                .contains("\"embeddingProvider\" : \"mock\"")
                .contains("\"scopeViolationRate\" : \"0.0000\"")
                .contains("\"answerHitRate\" : \"1.0000\"")
                .contains("\"multiDocumentCoverageRate\" : \"1.0000\"")
                .contains("\"groundedAnswerRate\" : \"1.0000\"")
                .contains("\"forbiddenAnswerLeakRate\" : \"0.0000\"")
                .contains("\"noEvidenceCitationFreeRate\" : \"1.0000\"")
                .contains("knowledge-base RAG eval cases")
                .doesNotContain("documentText")
                .doesNotContain("answerText")
                .doesNotContain("DocPilot redis session cache evidence")
                .doesNotContain("prompt")
                .doesNotContain("evidenceContext")
                .doesNotContain("Authorization")
                .doesNotContain("apiKey")
                .doesNotContain("secret");
    }
}
