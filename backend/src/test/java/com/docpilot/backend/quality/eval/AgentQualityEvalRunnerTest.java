package com.docpilot.backend.quality.eval;

import com.docpilot.backend.quality.vo.QualityEvalCaseResultDetail;
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
        assertThat(result.metrics().caseCount()).isGreaterThanOrEqualTo(7);
        assertThat(result.caseResults())
                .extracting(QualityEvalCaseResultDetail::caseId)
                .contains(
                        "short-document-rag-evidence",
                        "kb-two-document-coverage",
                        "citation-distractor-pruning",
                        "quality-console-startup-health",
                        "agent-document-search-route",
                        "agent-rag-answer-route",
                        "kb-agent-grounded-answer-route",
                        "document-parser-real-chain",
                        "memory-provider-small-sample"
                );
        assertThat(result.metrics().casePassRate()).isEqualTo(1.0D);
        assertThat(result.metrics().traceLinkedCaseCount()).isGreaterThanOrEqualTo(result.metrics().caseCount() - 1);
        assertThat(result.caseResults()).allSatisfy(caseResult -> {
            assertThat(caseResult.caseId()).isNotBlank();
            if (!"agent-document-search-route".equals(caseResult.caseId())) {
                assertThat(caseResult.traceId()).isNotBlank();
                assertThat(caseResult.agentRunId()).isNotBlank();
            }
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
                .doesNotContain("parsed short txt document")
                .doesNotContain("summary over two short documents")
                .doesNotContain("unrelated distractor citations")
                .doesNotContain("backend startup health")
                .doesNotContain("RAG retrieve topK chunks and show similarity score")
                .doesNotContain("retrieve evidence and answer what")
                .doesNotContain("knowledge base agent request")
                .doesNotContain("PDF, HTML and DOCX")
                .doesNotContain("real-provider memory extraction")
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

    @Test
    void shouldEvaluateExpectedDecisionWithoutStoringRawQuestion() throws Exception {
        AgentQualityEvalCase evalCase = new AgentQualityEvalCase(
                "agent-search-route-mismatch",
                "RAG retrieve topK chunks and show similarity score",
                "Expected behavior should stay out of artifact",
                List.of("documentSearchEvidence"),
                List.of("document_search_tool"),
                List.of("search_intent"),
                List.of("rag_answer_generated"),
                List.of("agent_search"),
                Map.of("expectedDecision", "rag_tool")
        );

        AgentQualityEvalResult result = new AgentQualityEvalRunner(new ObjectMapper().findAndRegisterModules())
                .evaluateDefaultCases();
        AgentQualityEvalResult mismatch = new AgentQualityEvalRunner(new ObjectMapper().findAndRegisterModules())
                .evaluate(List.of(evalCase), Map.of());

        assertThat(result.status()).isEqualTo("PASS");
        assertThat(mismatch.status()).isEqualTo("FAILED_CORE_FLOW");
        assertThat(mismatch.caseResults().get(0).failureBuckets())
                .contains("expectedToolMissing", "expectedDecisionMismatch");

        String serialized = new ObjectMapper().findAndRegisterModules().writeValueAsString(mismatch.toSafeMap());
        assertThat(serialized)
                .doesNotContain("RAG retrieve topK chunks and show similarity score")
                .doesNotContain("Expected behavior should stay out of artifact");
    }
}
