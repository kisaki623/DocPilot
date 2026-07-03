package com.docpilot.backend.memory.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryQualityEvalRunnerTest {

    @Test
    void shouldEvaluateDefaultCasesAndWriteSafeArtifact() throws Exception {
        MemoryQualityEvalRunner runner = new MemoryQualityEvalRunner();

        MemoryQualityEvalResult result = runner.evaluateDefaultCases();

        assertThat(result.failedCaseIds()).isEmpty();
        assertThat(result.metrics().caseCount()).isGreaterThanOrEqualTo(5);
        assertThat(result.metrics().casePassRate()).isEqualTo(1.0D);
        assertThat(result.metrics().suggestionTypeRecallRate()).isEqualTo(1.0D);
        assertThat(result.metrics().activeMemoryPrecisionRate()).isEqualTo(1.0D);
        assertThat(result.metrics().sensitiveRejectionRate()).isEqualTo(1.0D);
        assertThat(result.metrics().suggestionSafetyRate()).isEqualTo(1.0D);
        assertThat(result.metrics().ragEvidenceIsolationRate()).isEqualTo(1.0D);
        assertThat(result.metrics().userSignalExtractionRate()).isEqualTo(1.0D);
        assertThat(result.metrics().noiseSuppressionRate()).isEqualTo(1.0D);
        assertThat(result.metrics().temporaryInstructionSuppressionRate()).isEqualTo(1.0D);
        assertThat(result.metrics().traceSourceCountRate()).isEqualTo(1.0D);
        assertThat(result.metrics().providerBackedCaseRate()).isEqualTo(0.0D);
        assertThat(result.providerEvaluation().extractionProvider()).isEqualTo("rule_based");
        assertThat(result.providerEvaluation().status()).isEqualTo("not_configured");
        assertThat(result.providerEvaluation().realProviderConfigured()).isFalse();
        assertThat(result.providerEvaluation().modelCallCount()).isZero();
        assertThat(result.providerEvaluation().rawProviderOutputStored()).isFalse();
        assertThat(result.caseEvaluations()).allSatisfy(evaluation -> {
            assertThat(evaluation.extractionProvider()).isEqualTo("rule_based");
            assertThat(evaluation.providerBacked()).isFalse();
        });

        Path artifact = Path.of("target", "memory-eval", "memory-quality-eval-test.json");
        runner.writeArtifact(result, artifact);
        String json = Files.readString(artifact);

        assertThat(json).contains("casePassRate");
        assertThat(json).contains("suggestionSafetyRate");
        assertThat(json).contains("noiseSuppressionRate");
        assertThat(json).contains("traceSourceCountRate");
        assertThat(json).contains("providerBackedCaseRate");
        assertThat(json).contains("providerEvaluation");
        assertThat(json).contains("realProviderConfigured");
        assertThat(json).contains("rawProviderOutputStored");
        assertThat(json).doesNotContain(".env");
        assertThat(json).doesNotContain("RAG evidence:");
        assertThat(json).doesNotContain("以后请回答");
        assertThat(json).doesNotContain("redacted evidence 1");
    }

    @Test
    void shouldReportFailedCases() {
        MemoryQualityEvalCase evalCase = new MemoryQualityEvalCase(
                "bad-memory-case",
                "memory_status_isolation",
                7L,
                10L,
                "continue",
                "AGENT_MEMORY",
                null,
                false,
                true,
                "",
                java.util.List.of(),
                java.util.List.of(new MemoryQualityEvalCase.EvalMemory(
                        201L,
                        "ACTIVE",
                        "PREFERENCE",
                        "prefer concise answers",
                        40
                )),
                "",
                false,
                0,
                false,
                java.util.Map.of(),
                java.util.List.of("PREFERENCE"),
                java.util.List.of(999L),
                java.util.List.of(),
                java.util.List.of(),
                java.util.Map.of("conversationSummary", 0, "recentMessages", 0, "userMemory", 1, "ragEvidence", 0)
        );

        MemoryQualityEvalResult result = new MemoryQualityEvalRunner().evaluate(java.util.List.of(evalCase));

        assertThat(result.failedCaseIds()).containsExactly("bad-memory-case");
        assertThat(result.caseEvaluations().get(0).failureReasons()).contains("suggestion_types_mismatch");
        assertThat(result.caseEvaluations().get(0).failureReasons()).contains("active_memory_selection_mismatch");
    }
}
