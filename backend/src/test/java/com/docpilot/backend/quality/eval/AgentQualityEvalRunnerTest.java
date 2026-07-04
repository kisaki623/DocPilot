package com.docpilot.backend.quality.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentQualityEvalRunnerTest {

    @Test
    void shouldLoadDefaultCasesAndWriteSafeArtifact() throws Exception {
        AgentQualityEvalRunner runner = new AgentQualityEvalRunner();

        AgentQualityEvalResult result = runner.evaluateDefaultCases();

        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.metrics().caseCount()).isGreaterThanOrEqualTo(3);
        assertThat(result.metrics().casePassRate()).isEqualTo(1.0D);
        assertThat(result.metrics().traceLinkedCaseCount()).isEqualTo(result.metrics().caseCount());
        assertThat(result.caseResults()).allSatisfy(caseResult -> {
            assertThat(caseResult.caseId()).isNotBlank();
            assertThat(caseResult.traceId()).isNotBlank();
            assertThat(caseResult.agentRunId()).isNotBlank();
            assertThat(caseResult.failureBuckets()).isEmpty();
        });

        Path artifact = Path.of("target", "quality-eval", "agent-quality-eval-test.json");
        runner.writeArtifact(result, artifact);
        String json = Files.readString(artifact);

        assertThat(json)
                .contains("caseResults")
                .contains("casePassRate")
                .contains("rawQuestionStored")
                .contains("rawAnswerStored")
                .contains("rawEvidenceStored")
                .doesNotContain("Use the bound knowledge base")
                .doesNotContain("Agent should use RAG evidence")
                .doesNotContain("Answer with user memory")
                .doesNotContain("Ask an out-of-scope question")
                .doesNotContain("SYSTEM_PROMPT")
                .doesNotContain("RAW_ANSWER")
                .doesNotContain("DOCUMENT_FULL_TEXT");
    }

    @Test
    void shouldReportFailedCaseWithoutRawObservationText() throws Exception {
        AgentQualityEvalCase evalCase = new AgentQualityEvalCase(
                "agent-quality-failure",
                "Question text should stay out of artifact",
                "Expected behavior should stay out of artifact",
                List.of("ragEvidence"),
                List.of("rag_qa_tool"),
                List.of("citation_present"),
                List.of("unsupported_citation"),
                List.of("rag"),
                Map.of("requireTraceLink", true)
        );
        AgentQualityEvalObservation observation = new AgentQualityEvalObservation(
                "agent-quality-failure",
                Set.of(),
                Set.of("other_tool"),
                "unsupported_citation RAW_ANSWER_SHOULD_NOT_LEAK",
                "",
                ""
        );

        AgentQualityEvalResult result = new AgentQualityEvalRunner(new ObjectMapper().findAndRegisterModules())
                .evaluate(List.of(evalCase), Map.of(evalCase.caseId(), observation));

        assertThat(result.status()).isEqualTo("FAILED_CORE_FLOW");
        assertThat(result.caseResults().get(0).failureBuckets())
                .containsExactly(
                        "expectedEvidenceMissing",
                        "expectedToolMissing",
                        "mustContainMissing",
                        "mustNotContainViolation",
                        "traceLinkMissing"
                );

        String serialized = new ObjectMapper().findAndRegisterModules().writeValueAsString(result.toSafeMap());
        assertThat(serialized)
                .doesNotContain("Question text should stay out of artifact")
                .doesNotContain("Expected behavior should stay out of artifact")
                .doesNotContain("RAW_ANSWER_SHOULD_NOT_LEAK");
    }
}
