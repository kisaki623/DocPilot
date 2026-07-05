package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.service.QualityArtifactService;
import com.docpilot.backend.quality.vo.QualityEvalCaseResultDetail;
import com.docpilot.backend.quality.vo.QualityRunDetail;
import com.docpilot.backend.quality.vo.QualityRunSummary;
import com.docpilot.backend.quality.vo.QualityTokenUsageSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QualityEvalCatalogServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadSafeCatalogFieldsAndLatestStatus() throws Exception {
        writeCaseFixture("""
                [
                  {
                    "caseId": "agent-rag-evidence-trace",
                    "caseVersion": 2,
                    "owner": "quality-console",
                    "lastUpdated": "2026-07-05",
                    "riskLevel": "P1",
                    "caseLayer": "agent_rag_trace",
                    "riskGate": "FAILED_CORE_FLOW",
                    "scoringSummary": ["evidence_required", "tool_required", "trace_required"],
                    "regressionPolicy": ["quality_tests", "agent_quality_eval_smoke"],
                    "failureHistoryMarkers": ["marker=docpilot-agent-quality-eval:status=REVIEW:issue=REA-20260703-P1-001"],
                    "sourceIssueIds": ["REA-20260703-P1-001"],
                    "lastVerifiedMarker": "docpilot-cloud-quality-20260703213703-dbef08",
                    "remediationHints": ["check_short_document_chunk", "verify_quote_citation"],
                    "question": "sensitive question must not be returned",
                    "expectedBehavior": "internal behavior text must not be returned",
                    "expectedEvidence": ["ragEvidence"],
                    "expectedTools": ["rag_qa_tool"],
                    "mustContain": ["citation_present"],
                    "mustNotContain": ["no_evidence"],
                    "tags": ["rag", "trace"],
                    "scoringRules": {
                      "requireExpectedEvidence": true,
                      "requireTraceLink": true
                    }
                  }
                ]
                """);
        QualityArtifactService artifactService = mock(QualityArtifactService.class);
        when(artifactService.listRecentRuns(20)).thenReturn(List.of(summary("docpilot-agent-quality-eval")));
        when(artifactService.getRunDetail("docpilot-agent-quality-eval")).thenReturn(Optional.of(new QualityRunDetail(
                summary("docpilot-agent-quality-eval"),
                List.of(),
                List.of(new QualityEvalCaseResultDetail(
                        "agent-rag-evidence-trace",
                        "rag",
                        "REVIEW",
                        false,
                        "trace-agent-rag-evidence-trace",
                        "agent-run-agent-rag-evidence-trace",
                        List.of(),
                        List.of("citationNeedsReview"),
                        Map.of(),
                        Map.of()
                ))
        )));
        QualityEvalCatalogServiceImpl service = new QualityEvalCatalogServiceImpl(
                tempDir,
                new ObjectMapper(),
                artifactService
        );

        var items = service.listEvalCases();

        assertThat(items).hasSize(1);
        var item = items.get(0);
        assertThat(item.caseId()).isEqualTo("agent-rag-evidence-trace");
        assertThat(item.caseVersion()).isEqualTo(2);
        assertThat(item.owner()).isEqualTo("quality-console");
        assertThat(item.lastUpdated()).isEqualTo("2026-07-05");
        assertThat(item.riskLevel()).isEqualTo("P1");
        assertThat(item.caseLayer()).isEqualTo("agent_rag_trace");
        assertThat(item.riskGate()).isEqualTo("FAILED_CORE_FLOW");
        assertThat(item.scoringSummary()).containsExactly("evidence_required", "tool_required", "trace_required");
        assertThat(item.regressionPolicy()).containsExactly("quality_tests", "agent_quality_eval_smoke");
        assertThat(item.failureHistoryMarkers()).containsExactly("marker=docpilot-agent-quality-eval:status=REVIEW:issue=REA-20260703-P1-001");
        assertThat(item.sourceIssueIds()).containsExactly("REA-20260703-P1-001");
        assertThat(item.lastVerifiedMarker()).isEqualTo("docpilot-cloud-quality-20260703213703-dbef08");
        assertThat(item.remediationHints()).containsExactly("check_short_document_chunk", "verify_quote_citation");
        assertThat(item.caseType()).isEqualTo("rag");
        assertThat(item.tags()).containsExactly("rag", "trace");
        assertThat(item.expectedEvidence()).containsExactly("ragEvidence");
        assertThat(item.expectedTools()).containsExactly("rag_qa_tool");
        assertThat(item.scoringRules()).containsExactly("requireExpectedEvidence=true", "requireTraceLink=true");
        assertThat(item.latestStatus()).isEqualTo("REVIEW");
        assertThat(item.latestRunMarker()).isEqualTo("docpilot-agent-quality-eval");
        assertThat(item.latestTraceId()).isEqualTo("trace-agent-rag-evidence-trace");
        assertThat(item.latestAgentRunId()).isEqualTo("agent-run-agent-rag-evidence-trace");
        assertThat(item.latestReviewBuckets()).containsExactly("citationNeedsReview");
        assertThat(item.toString())
                .doesNotContain("sensitive question")
                .doesNotContain("internal behavior")
                .doesNotContain("citation_present")
                .doesNotContain("no_evidence");
    }

    @Test
    void shouldFilterUnsafeCatalogValues() throws Exception {
        writeCaseFixture("""
                [
                  {
                    "caseId": "safe-case",
                    "caseVersion": 3,
                    "owner": "apiKeyOwner",
                    "lastUpdated": "https://example.invalid/date",
                    "riskLevel": "P2",
                    "caseLayer": "rag",
                    "riskGate": "https://example.invalid/gate",
                    "scoringSummary": ["safe_rule", "secret_rule"],
                    "regressionPolicy": ["quality_tests", "bearer_bad"],
                    "failureHistoryMarkers": ["marker=docpilot-safe:status=PASS:issue=REA-20260703-P1-001", "https://example.invalid/history"],
                    "sourceIssueIds": ["REA-20260703-P1-001", "https://example.invalid/issue"],
                    "lastVerifiedMarker": "http://example.invalid/run",
                    "remediationHints": ["check_kb_document_coverage", "secret_hint"],
                    "expectedEvidence": ["ragEvidence", "https://example.invalid/evidence"],
                    "expectedTools": ["rag_qa_tool", "secretTool"],
                    "tags": ["rag", "accessToken"],
                    "scoringRules": {
                      "requireTraceLink": true,
                      "apiKeyRule": true
                    }
                  }
                ]
                """);
        QualityEvalCatalogServiceImpl service = new QualityEvalCatalogServiceImpl(
                tempDir,
                new ObjectMapper(),
                mock(QualityArtifactService.class)
        );

        var item = service.listEvalCases().get(0);

        assertThat(item.caseVersion()).isEqualTo(3);
        assertThat(item.owner()).isEmpty();
        assertThat(item.lastUpdated()).isEmpty();
        assertThat(item.riskLevel()).isEqualTo("P2");
        assertThat(item.caseLayer()).isEqualTo("rag");
        assertThat(item.riskGate()).isEmpty();
        assertThat(item.scoringSummary()).containsExactly("safe_rule");
        assertThat(item.regressionPolicy()).containsExactly("quality_tests");
        assertThat(item.failureHistoryMarkers()).containsExactly("marker=docpilot-safe:status=PASS:issue=REA-20260703-P1-001");
        assertThat(item.sourceIssueIds()).containsExactly("REA-20260703-P1-001");
        assertThat(item.lastVerifiedMarker()).isEmpty();
        assertThat(item.remediationHints()).containsExactly("check_kb_document_coverage");
        assertThat(item.expectedEvidence()).containsExactly("ragEvidence");
        assertThat(item.expectedTools()).containsExactly("rag_qa_tool");
        assertThat(item.tags()).containsExactly("rag");
        assertThat(item.scoringRules()).containsExactly("requireTraceLink=true");
        assertThat(item.latestStatus()).isEqualTo("NOT_RUN");
    }

    @Test
    void shouldReturnEmptyListWhenCaseFileMissing() {
        QualityEvalCatalogServiceImpl service = new QualityEvalCatalogServiceImpl(
                tempDir,
                new ObjectMapper(),
                mock(QualityArtifactService.class)
        );

        assertThat(service.listEvalCases()).isEmpty();
    }

    private void writeCaseFixture(String content) throws Exception {
        Path fixture = tempDir.resolve("backend/src/test/resources/quality/agent-quality-eval-cases.json");
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, content);
    }

    private QualityRunSummary summary(String marker) {
        return new QualityRunSummary(
                marker,
                "backend/target/agent-quality-eval",
                marker + "/artifact.json",
                "PASS",
                Instant.parse("2026-07-05T00:00:00Z"),
                1,
                0,
                0,
                List.of(),
                List.of(),
                QualityTokenUsageSummary.empty(),
                false,
                false
        );
    }
}
