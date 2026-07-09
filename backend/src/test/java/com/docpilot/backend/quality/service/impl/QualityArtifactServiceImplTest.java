package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.vo.QualityEvalCaseResultDetail;
import com.docpilot.backend.quality.vo.QualityRunDetail;
import com.docpilot.backend.quality.vo.QualityRunSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityArtifactServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path repoRoot;

    @Test
    void shouldReturnEmptyListWhenArtifactRootsAreMissing() {
        QualityArtifactServiceImpl service = new QualityArtifactServiceImpl(repoRoot, objectMapper);

        assertThat(service.listRecentRuns(20)).isEmpty();
        assertThat(service.getRunDetail("missing")).isEmpty();
    }

    @Test
    void shouldResolveRepoRootFromNestedBackendWorkingDirectory() throws Exception {
        Path backendClasses = repoRoot.resolve("backend/target/classes");
        Files.createDirectories(backendClasses);
        Files.createDirectories(repoRoot.resolve("frontend"));
        Files.createDirectories(repoRoot.resolve("docs"));

        assertThat(QualityArtifactServiceImpl.normalizeRepoRoot(backendClasses))
                .isEqualTo(repoRoot.toAbsolutePath().normalize());
    }

    @Test
    void shouldParseNormalArtifactWithWhitelistedFieldsOnly() throws Exception {
        Path artifact = artifactPath("backend/target/rag-real-qa", "docpilot-quality-normal", "artifact.json");
        Files.writeString(artifact, """
                {
                  "smokeMarker": "docpilot-quality-normal",
                  "status": "PASS",
                  "prompt": "SYSTEM_PROMPT_SHOULD_NOT_LEAK",
                  "answer": "RAW_ANSWER_SHOULD_NOT_LEAK",
                  "documentText": "DOCUMENT_FULL_TEXT_SHOULD_NOT_LEAK",
                  "secret": "SECRET_SHOULD_NOT_LEAK",
                  "connectionString": "CONNECTION_STRING_SHOULD_NOT_LEAK",
                  "token_usage": {
                    "prompt_tokens": 12,
                    "completion_tokens": 5,
                    "total_tokens": 17,
                    "estimated_cost": 0.01
                  },
                  "naturalCorpus": {
                    "status": "PASS",
                    "passed": true,
                    "casePassRate": 1.0,
                    "answerFaithfulnessPassCount": 3,
                    "modelCallCount": 2,
                    "toolCallCount": 4,
                    "retryCount": 1,
                    "latencyMs": 1250,
                    "durationMs": 1300,
                    "ragTriggered": true,
                    "answerText": "NATURAL_ANSWER_SHOULD_NOT_LEAK",
                    "failureBuckets": [],
                    "reviewBuckets": ["manualReview"]
                  },
                  "caseResults": [
                    {
                      "caseId": "case-safe-1",
                      "caseType": "rag",
                      "passed": true,
                      "traceId": "trace-1",
                      "agentRunId": "agent-run-1",
                      "conversationId": 17,
                      "question": "QUESTION_SHOULD_NOT_LEAK",
                      "failureBuckets": [],
                      "reviewBuckets": []
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        QualityArtifactServiceImpl service = new QualityArtifactServiceImpl(repoRoot, objectMapper);

        List<QualityRunSummary> runs = service.listRecentRuns(20);
        assertThat(runs).hasSize(1);
        QualityRunSummary summary = runs.get(0);
        assertThat(summary.marker()).isEqualTo("docpilot-quality-normal");
        assertThat(summary.status()).isEqualTo("PASS");
        assertThat(summary.gateCount()).isEqualTo(1);
        assertThat(summary.reviewBuckets()).containsExactly("manualReview");
        assertThat(summary.tokenUsage().promptTokens()).isEqualTo(12);
        assertThat(summary.tokenUsage().completionTokens()).isEqualTo(5);
        assertThat(summary.tokenUsage().totalTokens()).isEqualTo(17);
        assertThat(summary.tokenUsage().estimatedCost()).isEqualByComparingTo("0.01");

        QualityRunDetail detail = service.getRunDetail("docpilot-quality-normal").orElseThrow();
        assertThat(detail.gates()).hasSize(1);
        assertThat(detail.gates().get(0).metrics())
                .containsEntry("casePassRate", 1.0)
                .containsEntry("answerFaithfulnessPassCount", 3)
                .containsEntry("modelCallCount", 2)
                .containsEntry("toolCallCount", 4)
                .containsEntry("retryCount", 1)
                .containsEntry("latencyMs", 1250)
                .containsEntry("durationMs", 1300);
        assertThat(detail.gates().get(0).flags()).containsEntry("ragTriggered", true);
        assertThat(detail.evalCases()).hasSize(1);
        assertThat(detail.evalCases().get(0).caseId()).isEqualTo("case-safe-1");
        assertThat(detail.evalCases().get(0).metrics()).isEmpty();
        assertThat(detail.evalCases().get(0).flags()).isEmpty();
        assertThat(detail.traceReferences()).hasSize(1);
        assertThat(detail.traceReferences().get(0).caseId()).isEqualTo("case-safe-1");
        assertThat(detail.traceReferences().get(0).traceId()).isEqualTo("trace-1");
        assertThat(detail.traceReferences().get(0).agentRunId()).isEqualTo("agent-run-1");
        assertThat(detail.traceReferences().get(0).conversationId()).isEqualTo("17");
        assertThat(detail.traceReferences().get(0).steps())
                .extracting(step -> step.stepType())
                .containsExactly("eval_case", "agent_step");

        String serialized = objectMapper.writeValueAsString(detail);
        assertThat(serialized)
                .doesNotContain("SYSTEM_PROMPT_SHOULD_NOT_LEAK")
                .doesNotContain("RAW_ANSWER_SHOULD_NOT_LEAK")
                .doesNotContain("DOCUMENT_FULL_TEXT_SHOULD_NOT_LEAK")
                .doesNotContain("SECRET_SHOULD_NOT_LEAK")
                .doesNotContain("CONNECTION_STRING_SHOULD_NOT_LEAK")
                .doesNotContain("NATURAL_ANSWER_SHOULD_NOT_LEAK")
                .doesNotContain("QUESTION_SHOULD_NOT_LEAK");
    }

    @Test
    void shouldParseNestedCloudQualityGatesAndSafeEvalCaseSignals() throws Exception {
        Path artifact = artifactPath("backend/target/audit", "docpilot-cloud-quality-nested", "artifact.json");
        Files.writeString(artifact, """
                {
                  "smokeMarker": "docpilot-cloud-quality-nested",
                  "overallStatus": "PASS",
                  "gates": {
                    "naturalCorpus": {
                      "status": "PASS",
                      "checks": [
                        {
                          "casePassRate": 1.0,
                          "distractorCitationFreeCount": 25,
                          "answerFaithfulnessPassCount": 11,
                          "citationPhraseSupportPassCount": 22,
                          "traceRagTriggered": true,
                          "traceRagRequired": true,
                          "hardFailureBuckets": [],
                          "reviewBuckets": [],
                          "caseResults": [
                            {
                              "caseId": "ops-incident-support-summary",
                              "caseType": "natural_multi_doc_summary",
                              "retrieveHits": 4,
                              "qaCitations": 2,
                              "distractorCitationCount": 0,
                              "targetCitationCovered": true,
                              "noEvidenceCorrect": true,
                              "expectedEvidenceSupported": true,
                              "traceId": "trace-natural-1",
                              "agentRunId": "agent-natural-1",
                              "conversationId": "conversation-1",
                              "question": "QUESTION_SHOULD_NOT_LEAK",
                              "answerText": "ANSWER_SHOULD_NOT_LEAK",
                              "evidenceContext": "EVIDENCE_SHOULD_NOT_LEAK",
                              "failureBuckets": [],
                              "reviewBuckets": []
                            }
                          ]
                        }
                      ]
                    },
                    "frontendInteraction": {
                      "status": "PASS",
                      "checks": [
                        { "consoleErrorCount": 0, "permissionMessageVisible": true }
                      ],
                      "caseEvaluations": [
                        {
                          "caseId": "frontend-permission-message",
                          "caseType": "frontend_permission",
                          "status": "REVIEW",
                          "traceId": "trace-frontend-1",
                          "failureBuckets": [],
                          "reviewBuckets": ["permissionUx"]
                        }
                      ]
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        QualityArtifactServiceImpl service = new QualityArtifactServiceImpl(repoRoot, objectMapper);

        QualityRunDetail detail = service.getRunDetail("docpilot-cloud-quality-nested").orElseThrow();

        assertThat(detail.summary().status()).isEqualTo("PASS");
        assertThat(detail.summary().gateCount()).isEqualTo(2);
        assertThat(detail.gates()).extracting(gate -> gate.name())
                .containsExactly("naturalCorpus", "frontendInteraction");
        assertThat(detail.gates().get(0).metrics())
                .containsEntry("checkCount", 1)
                .containsEntry("casePassRate", 1.0)
                .containsEntry("distractorCitationFreeCount", 25);
        assertThat(detail.gates().get(0).flags())
                .containsEntry("traceRagTriggered", true)
                .containsEntry("traceRagRequired", true);
        assertThat(detail.evalCases()).hasSize(2);
        assertThat(detail.evalCases()).extracting(QualityEvalCaseResultDetail::caseId)
                .containsExactly("ops-incident-support-summary", "frontend-permission-message");
        assertThat(detail.evalCases().get(0).metrics())
                .containsEntry("retrieveHits", 4);
        assertThat(detail.traceReferences()).hasSize(2);
        assertThat(detail.traceReferences()).extracting(ref -> ref.caseId())
                .containsExactly("frontend-permission-message", "ops-incident-support-summary");
        assertThat(detail.traceReferences().get(0).gateName()).isEqualTo("frontendInteraction");
        assertThat(detail.traceReferences().get(0).reviewBuckets()).containsExactly("permissionUx");
        assertThat(detail.traceReferences().get(1).gateName()).isEqualTo("naturalCorpus");
        assertThat(detail.traceReferences().get(1).conversationId()).isEqualTo("conversation-1");
        assertThat(detail.traceReferences().get(1).steps())
                .extracting(step -> step.stepType())
                .contains("eval_case", "agent_step", "rag_retrieve", "citation");
        assertThat(detail.traceReferences().get(1).steps())
                .anySatisfy(step -> {
                    assertThat(step.stepType()).isEqualTo("rag_retrieve");
                    assertThat(step.metrics()).containsEntry("retrieveHits", 4);
                })
                .anySatisfy(step -> {
                    assertThat(step.stepType()).isEqualTo("citation");
                    assertThat(step.metrics()).containsEntry("qaCitations", 2);
                });
        assertThat(detail.traceReferences().get(0).steps())
                .extracting(step -> step.stepType())
                .contains("failure_bucket");

        String serialized = objectMapper.writeValueAsString(detail);
        assertThat(serialized)
                .doesNotContain("QUESTION_SHOULD_NOT_LEAK")
                .doesNotContain("ANSWER_SHOULD_NOT_LEAK")
                .doesNotContain("EVIDENCE_SHOULD_NOT_LEAK");
    }

    @Test
    void shouldParseDocumentParserSmokeArtifactAsQualityRun() throws Exception {
        Path artifact = artifactPath("backend/target/smoke/document-parser-real-chain",
                "docpilot-parser-real-chain", "artifact.json");
        Files.writeString(artifact, """
                {
                  "marker": "docpilot-parser-real-chain",
                  "status": "PASS",
                  "gates": {
                    "parserRealChain": {
                      "status": "PASS",
                      "fileCount": 3,
                      "parsedFileCount": 3,
                      "parserFailureCount": 0,
                      "chunkCount": 3,
                      "retrieveHitCount": 3,
                      "directRetrieveHitCount": 1,
                      "qaRetrievalHitCount": 3,
                      "citationCount": 3,
                      "sourceLocatorCount": 3,
                      "durationMs": 12000,
                      "retrieveHit": true,
                      "citationPresent": true,
                      "sourceLocatorPresent": true
                    }
                  },
                  "files": [
                    {
                      "fileType": "PDF",
                      "parserName": "pdfbox",
                      "parseStatus": "SUCCESS",
                      "extractedChars": 200,
                      "chunkCount": 1,
                      "retrieveHit": true,
                      "citationPresent": true,
                      "sourceLocatorPresent": true,
                      "prompt": "PROMPT_SHOULD_NOT_LEAK",
                      "answer": "ANSWER_SHOULD_NOT_LEAK",
                      "content": "DOCUMENT_TEXT_SHOULD_NOT_LEAK"
                    }
                  ],
                  "parserQualityReport": {
                    "schemaVersion": 1,
                    "qualityStatus": "PASS",
                    "fileTypeCoverage": {
                      "expectedTypes": ["PDF", "HTML", "DOCX"],
                      "coveredTypes": ["PDF", "HTML", "DOCX"],
                      "missingTypes": [],
                      "allCovered": true
                    },
                    "parseStatusSummary": {
                      "fileCount": 3,
                      "parsedFileCount": 3,
                      "parserFailureCount": 0,
                      "parsePassRate": 1.0
                    },
                    "sourceLocatorSummary": {
                      "sourceLocatorCount": 3,
                      "fileCount": 3,
                      "sourceLocatorCoverageRate": 1.0,
                      "missingLocatorTypes": []
                    },
                    "ragChainSummary": {
                      "chunkCountKnown": 3,
                      "chunkCount": 3,
                      "retrieveHitCount": 3,
                      "directRetrieveHitCount": 1,
                      "qaRetrievalHitCount": 3,
                      "citationCount": 3,
                      "retrieveCoverageRate": 1.0,
                      "citationCoverageRate": 1.0
                    },
                    "boundarySummary": {
                      "negativeCaseCount": 4,
                      "negativeCasePassCount": 4,
                      "negativeCaseFailCount": 0,
                      "boundaryPassRate": 1.0,
                      "unsupportedUploadRejected": true
                    },
                    "warningsSummary": {
                      "warningCountKnown": 1,
                      "totalWarningCount": 0,
                      "filesWithWarnings": 0
                    },
                    "reviewReasons": [],
                    "unavailableMetrics": ["warningCount"],
                    "prompt": "PROMPT_IN_REPORT_SHOULD_NOT_LEAK",
                    "answer": "ANSWER_IN_REPORT_SHOULD_NOT_LEAK",
                    "content": "CONTENT_IN_REPORT_SHOULD_NOT_LEAK"
                  }
                }
                """, StandardCharsets.UTF_8);

        QualityArtifactServiceImpl service = new QualityArtifactServiceImpl(repoRoot, objectMapper);

        QualityRunDetail detail = service.getRunDetail("docpilot-parser-real-chain").orElseThrow();

        assertThat(detail.summary().source())
                .isEqualTo("backend/target/smoke/document-parser-real-chain");
        assertThat(detail.summary().status()).isEqualTo("PASS");
        assertThat(detail.gates()).hasSize(1);
        assertThat(detail.gates().get(0).name()).isEqualTo("parserRealChain");
        assertThat(detail.gates().get(0).metrics())
                .containsEntry("fileCount", 3)
                .containsEntry("parsedFileCount", 3)
                .containsEntry("chunkCount", 3)
                .containsEntry("directRetrieveHitCount", 1)
                .containsEntry("qaRetrievalHitCount", 3)
                .containsEntry("citationCount", 3)
                .containsEntry("durationMs", 12000);
        assertThat(detail.gates().get(0).flags())
                .containsEntry("retrieveHit", true)
                .containsEntry("citationPresent", true)
                .containsEntry("sourceLocatorPresent", true);
        assertThat(detail.diagnostics().parserQuality().allFileTypesCovered()).isTrue();
        assertThat(detail.diagnostics().parserQuality().fileCount()).isEqualTo(3);
        assertThat(detail.diagnostics().parserQuality().parsedFileCount()).isEqualTo(3);
        assertThat(detail.diagnostics().parserQuality().parsePassRate()).isEqualTo(1.0);
        assertThat(detail.diagnostics().parserQuality().sourceLocatorCoverageRate()).isEqualTo(1.0);
        assertThat(detail.diagnostics().parserQuality().directRetrieveHitCount()).isEqualTo(1);
        assertThat(detail.diagnostics().parserQuality().qaRetrievalHitCount()).isEqualTo(3);
        assertThat(detail.diagnostics().parserQuality().boundaryPassRate()).isEqualTo(1.0);
        assertThat(detail.diagnostics().parserQuality().unavailableMetrics()).containsExactly("warningCount");

        String serialized = objectMapper.writeValueAsString(detail);
        assertThat(serialized)
                .doesNotContain("PROMPT_SHOULD_NOT_LEAK")
                .doesNotContain("ANSWER_SHOULD_NOT_LEAK")
                .doesNotContain("DOCUMENT_TEXT_SHOULD_NOT_LEAK")
                .doesNotContain("PROMPT_IN_REPORT_SHOULD_NOT_LEAK")
                .doesNotContain("ANSWER_IN_REPORT_SHOULD_NOT_LEAK")
                .doesNotContain("CONTENT_IN_REPORT_SHOULD_NOT_LEAK");
    }

    @Test
    void shouldBuildSafeDiagnosticsWithoutLeakingDocumentKeys() throws Exception {
        Path artifact = artifactPath("backend/target/audit", "docpilot-quality-diagnostics", "artifact.json");
        Files.writeString(artifact, """
                {
                  "smokeMarker": "docpilot-quality-diagnostics",
                  "status": "REVIEW",
                  "naturalCorpus": {
                    "status": "REVIEW",
                    "documentHitCounts": {
                      "private-doc-alpha": 3,
                      "private-doc-beta": 0
                    },
                    "contextSourceCounts": {
                      "userMemory": 2,
                      "ragEvidence": 5
                    },
                    "toolCallCount": 4,
                    "memoryCount": 2,
                    "reviewBuckets": ["toolArgsReview", "memoryGovernanceReview"],
                    "prompt": "PROMPT_SHOULD_NOT_LEAK",
                    "answerText": "ANSWER_SHOULD_NOT_LEAK",
                    "evidenceContext": "EVIDENCE_SHOULD_NOT_LEAK"
                  }
                }
                """, StandardCharsets.UTF_8);

        QualityArtifactServiceImpl service = new QualityArtifactServiceImpl(repoRoot, objectMapper);

        QualityRunDetail detail = service.getRunDetail("docpilot-quality-diagnostics").orElseThrow();

        assertThat(detail.diagnostics().documentCoverage().documentCount()).isEqualTo(2);
        assertThat(detail.diagnostics().documentCoverage().coveredDocumentCount()).isEqualTo(1);
        assertThat(detail.diagnostics().documentCoverage().zeroHitDocumentCount()).isEqualTo(1);
        assertThat(detail.diagnostics().documentCoverage().maxHitsPerDocument()).isEqualTo(3);
        assertThat(detail.diagnostics().documentCoverage().minHitsPerDocument()).isEqualTo(0);
        assertThat(detail.diagnostics().toolQuality().toolCallCount()).isEqualTo(4);
        assertThat(detail.diagnostics().toolQuality().toolArgsReviewCount()).isEqualTo(1);
        assertThat(detail.diagnostics().memoryQuality().memoryHitCount()).isEqualTo(2);
        assertThat(detail.diagnostics().memoryQuality().memoryReviewCount()).isEqualTo(1);
        assertThat(detail.diagnostics().memoryQuality().ragEvidenceCount()).isEqualTo(5);

        String serialized = objectMapper.writeValueAsString(detail);
        assertThat(serialized)
                .doesNotContain("private-doc-alpha")
                .doesNotContain("private-doc-beta")
                .doesNotContain("PROMPT_SHOULD_NOT_LEAK")
                .doesNotContain("ANSWER_SHOULD_NOT_LEAK")
                .doesNotContain("EVIDENCE_SHOULD_NOT_LEAK");
    }

    @Test
    void shouldMarkBadJsonAsReviewWithoutLeakingRawContent() throws Exception {
        Path artifact = artifactPath("backend/target/audit", "docpilot-quality-bad-json", "artifact.json");
        Files.writeString(artifact, "{ \"smokeMarker\": \"docpilot-quality-bad-json\", \"secret\": \"BAD_SECRET\" ",
                StandardCharsets.UTF_8);

        QualityArtifactServiceImpl service = new QualityArtifactServiceImpl(repoRoot, objectMapper);

        QualityRunSummary summary = service.listRecentRuns(20).get(0);
        assertThat(summary.marker()).isEqualTo("docpilot-quality-bad-json");
        assertThat(summary.status()).isEqualTo("REVIEW");
        assertThat(summary.artifactParseFailed()).isTrue();
        assertThat(summary.failureBuckets()).containsExactly("artifactParseFailed");

        String serialized = objectMapper.writeValueAsString(service.getRunDetail("docpilot-quality-bad-json").orElseThrow());
        assertThat(serialized).doesNotContain("BAD_SECRET");
    }

    @Test
    void shouldLimitRecentRunsByModifiedTime() throws Exception {
        Instant base = Instant.parse("2026-07-04T00:00:00Z");
        for (int i = 0; i < 25; i++) {
            String marker = "docpilot-quality-run-" + i;
            Path artifact = artifactPath("backend/target/memory-quality", marker, "artifact.json");
            Files.writeString(artifact, "{\"marker\":\"" + marker + "\",\"status\":\"PASS\"}", StandardCharsets.UTF_8);
            Files.setLastModifiedTime(artifact, FileTime.from(base.plusSeconds(i)));
        }

        QualityArtifactServiceImpl service = new QualityArtifactServiceImpl(repoRoot, objectMapper);

        List<QualityRunSummary> runs = service.listRecentRuns(20);
        assertThat(runs).hasSize(20);
        assertThat(runs.get(0).marker()).isEqualTo("docpilot-quality-run-24");
        assertThat(runs.get(19).marker()).isEqualTo("docpilot-quality-run-5");
        assertThat(service.listRecentRuns(3))
                .extracting(QualityRunSummary::marker)
                .containsExactly("docpilot-quality-run-24", "docpilot-quality-run-23", "docpilot-quality-run-22");
    }

    @Test
    void shouldBuildTrendSummaryFromRecentSafeDetails() throws Exception {
        Instant base = Instant.parse("2026-07-04T00:00:00Z");
        writeTrendArtifact("docpilot-trend-old", "PASS", 0.75, 100, "case-repeat", "REVIEW", "citationNeedsReview", base);
        writeTrendArtifact("docpilot-trend-mid", "REVIEW", 0.50, 200, "case-repeat", "REVIEW", "citationNeedsReview", base.plusSeconds(60));
        writeTrendArtifact("docpilot-trend-new", "FAILED_CORE_FLOW", 0.25, 300, "case-other", "FAILED_CORE_FLOW", "retrievalMiss", base.plusSeconds(120));

        QualityArtifactServiceImpl service = new QualityArtifactServiceImpl(repoRoot, objectMapper);

        var trend = service.getTrendSummary(3);

        assertThat(trend.runCount()).isEqualTo(3);
        assertThat(trend.statusCounts()).containsEntry("PASS", 1)
                .containsEntry("REVIEW", 1)
                .containsEntry("FAILED_CORE_FLOW", 1);
        assertThat(trend.reviewBucketCounts()).containsEntry("citationNeedsReview", 2);
        assertThat(trend.failureBucketCounts()).containsEntry("retrievalMiss", 1);
        assertThat(trend.totalTokens()).isEqualTo(600);
        assertThat(trend.estimatedCost()).isEqualTo(0.06);
        assertThat(trend.averageCasePassRate()).isEqualTo(0.5);
        assertThat(trend.points()).extracting(point -> point.marker())
                .containsExactly("docpilot-trend-new", "docpilot-trend-mid", "docpilot-trend-old");
        assertThat(trend.repeatedCases()).hasSize(1);
        assertThat(trend.repeatedCases().get(0).caseId()).isEqualTo("case-repeat");

        String serialized = objectMapper.writeValueAsString(trend);
        assertThat(serialized)
                .doesNotContain("RAW_ANSWER_SHOULD_NOT_LEAK")
                .doesNotContain("QUESTION_SHOULD_NOT_LEAK");
    }

    @Test
    void shouldReadLegacyAuditReportName() throws Exception {
        Path artifact = artifactPath("backend/target/audit", "docpilot-real-audit-legacy", "real-experience-audit-report.json");
        Files.writeString(artifact, """
                {
                  "marker": "docpilot-real-audit-legacy",
                  "status": "REVIEW",
                  "frontendInteraction": {
                    "passed": false,
                    "failureBuckets": ["quoteFirstUi"]
                  }
                }
                """, StandardCharsets.UTF_8);

        QualityArtifactServiceImpl service = new QualityArtifactServiceImpl(repoRoot, objectMapper);

        QualityRunDetail detail = service.getRunDetail("docpilot-real-audit-legacy").orElseThrow();
        assertThat(detail.summary().status()).isEqualTo("REVIEW");
        assertThat(detail.summary().failureBuckets()).containsExactly("quoteFirstUi");
        assertThat(detail.gates()).extracting(gate -> gate.name()).containsExactly("frontendInteraction");
    }

    @Test
    void shouldScanAgentQualityEvalArtifactRoot() throws Exception {
        Path artifact = artifactPath("backend/target/agent-quality-eval", "docpilot-agent-quality-eval-test", "artifact.json");
        Files.writeString(artifact, """
                {
                  "smokeMarker": "docpilot-agent-quality-eval-test",
                  "status": "PASS",
                  "agentQualityEval": {
                    "status": "PASS",
                    "passed": true,
                    "caseCount": 3,
                    "casePassRate": 1.0
                  }
                }
                """, StandardCharsets.UTF_8);

        QualityArtifactServiceImpl service = new QualityArtifactServiceImpl(repoRoot, objectMapper);

        QualityRunDetail detail = service.getRunDetail("docpilot-agent-quality-eval-test").orElseThrow();
        assertThat(detail.summary().source()).isEqualTo("backend/target/agent-quality-eval");
        assertThat(detail.gates()).extracting(gate -> gate.name()).containsExactly("agentQualityEval");
    }

    @Test
    void shouldScanAgentSearchRouteArtifactRoots() throws Exception {
        Path documentArtifact = artifactPath("backend/target/agent-search-route",
                "docpilot-agent-search-route-test", "artifact.json");
        Files.writeString(documentArtifact, """
                {
                  "smokeMarker": "docpilot-agent-search-route-test",
                  "status": "PASS",
                  "passed": true,
                  "caseCount": 2,
                  "searchDecisionPass": true,
                  "ragDecisionPass": true,
                  "caseResults": [
                    {
                      "caseId": "agent-document-search-route",
                      "caseType": "agent_search_route",
                      "status": "PASS",
                      "passed": true,
                      "searchDecisionPass": true,
                      "prompt": "PROMPT_SHOULD_NOT_LEAK",
                      "answer": "ANSWER_SHOULD_NOT_LEAK"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        Path kbArtifact = artifactPath("backend/target/agent-kb-search-route",
                "docpilot-agent-kb-search-route-test", "artifact.json");
        Files.writeString(kbArtifact, """
                {
                  "smokeMarker": "docpilot-agent-kb-search-route-test",
                  "status": "REVIEW",
                  "passed": false,
                  "caseCount": 3,
                  "kbSearchDecisionPass": false,
                  "unsupportedIntentPass": true,
                  "scopeFailurePropagated": true,
                  "failureBuckets": ["kbSearchDecisionMismatch"],
                  "caseResults": [
                    {
                      "caseId": "agent-kb-search-route",
                      "caseType": "agent_kb_search_route",
                      "status": "REVIEW",
                      "passed": false,
                      "expectedDecisionMatched": 0,
                      "failureBuckets": ["kbSearchDecisionMismatch"],
                      "documentText": "DOCUMENT_TEXT_SHOULD_NOT_LEAK",
                      "secret": "SECRET_SHOULD_NOT_LEAK"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        QualityArtifactServiceImpl service = new QualityArtifactServiceImpl(repoRoot, objectMapper);

        QualityRunDetail documentDetail = service.getRunDetail("docpilot-agent-search-route-test").orElseThrow();
        assertThat(documentDetail.summary().source()).isEqualTo("backend/target/agent-search-route");
        assertThat(documentDetail.evalCases()).extracting(QualityEvalCaseResultDetail::caseType)
                .containsExactly("agent_search_route");
        assertThat(documentDetail.evalCases().get(0).flags()).containsEntry("searchDecisionPass", true);
        assertThat(documentDetail.toString()).doesNotContain("PROMPT_SHOULD_NOT_LEAK", "ANSWER_SHOULD_NOT_LEAK");

        QualityRunDetail kbDetail = service.getRunDetail("docpilot-agent-kb-search-route-test").orElseThrow();
        assertThat(kbDetail.summary().source()).isEqualTo("backend/target/agent-kb-search-route");
        assertThat(kbDetail.summary().failureBuckets()).containsExactly("kbSearchDecisionMismatch");
        assertThat(kbDetail.evalCases()).extracting(QualityEvalCaseResultDetail::caseType)
                .containsExactly("agent_kb_search_route");
        assertThat(kbDetail.evalCases().get(0).failureBuckets()).containsExactly("kbSearchDecisionMismatch");
        assertThat(kbDetail.toString()).doesNotContain("DOCUMENT_TEXT_SHOULD_NOT_LEAK", "SECRET_SHOULD_NOT_LEAK");
    }

    @Test
    void shouldPromoteKnowledgeBaseAgentGateSafeFlags() throws Exception {
        Path artifact = artifactPath("tmp-e2e/docpilot-cloud-quality-smoke",
                "docpilot-cloud-quality-kb-agent", "artifact.json");
        Files.writeString(artifact, """
                {
                  "smokeMarker": "docpilot-cloud-quality-kb-agent",
                  "status": "PASS",
                  "gates": {
                    "knowledgeBaseAgent": {
                      "status": "PASS",
                      "checks": [
                        {
                          "success": true,
                          "decision": "search_tool",
                          "selectedTools": ["knowledge_base_search_tool"],
                          "retrieveHits": 6,
                          "citations": 6,
                          "coversBothDocuments": true,
                          "unsupportedIntentRejected": true,
                          "foreignKnowledgeBaseRejected": true,
                          "prompt": "PROMPT_SHOULD_NOT_LEAK",
                          "answer": "ANSWER_SHOULD_NOT_LEAK"
                        }
                      ]
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        QualityArtifactServiceImpl service = new QualityArtifactServiceImpl(repoRoot, objectMapper);

        QualityRunDetail detail = service.getRunDetail("docpilot-cloud-quality-kb-agent").orElseThrow();

        assertThat(detail.summary().source()).isEqualTo("tmp-e2e/docpilot-cloud-quality-smoke");
        assertThat(detail.gates()).hasSize(1);
        assertThat(detail.gates().get(0).name()).isEqualTo("knowledgeBaseAgent");
        assertThat(detail.gates().get(0).metrics())
                .containsEntry("retrieveHits", 6)
                .containsEntry("citations", 6);
        assertThat(detail.gates().get(0).flags())
                .containsEntry("coversBothDocuments", true)
                .containsEntry("unsupportedIntentRejected", true)
                .containsEntry("foreignKnowledgeBaseRejected", true);
        assertThat(detail.toString()).doesNotContain("PROMPT_SHOULD_NOT_LEAK", "ANSWER_SHOULD_NOT_LEAK");
    }

    private Path artifactPath(String root, String marker, String fileName) throws Exception {
        Path dir = repoRoot.resolve(root).resolve(marker);
        Files.createDirectories(dir);
        return dir.resolve(fileName);
    }

    private void writeTrendArtifact(String marker,
                                    String status,
                                    double casePassRate,
                                    int totalTokens,
                                    String caseId,
                                    String caseStatus,
                                    String bucket,
                                    Instant updatedAt) throws Exception {
        Path artifact = artifactPath("backend/target/audit", marker, "artifact.json");
        String bucketField = caseStatus.startsWith("FAILED") ? "failureBuckets" : "reviewBuckets";
        Files.writeString(artifact, """
                {
                  "smokeMarker": "%s",
                  "status": "%s",
                  "answer": "RAW_ANSWER_SHOULD_NOT_LEAK",
                  "token_usage": { "total_tokens": %d, "estimated_cost": 0.02 },
                  "naturalCorpus": {
                    "status": "%s",
                    "casePassRate": %s,
                    "latencyMs": 100,
                    "durationMs": 120,
                    "caseResults": [
                      {
                        "caseId": "%s",
                        "caseType": "rag",
                        "status": "%s",
                        "traceId": "trace-%s",
                        "question": "QUESTION_SHOULD_NOT_LEAK",
                        "%s": ["%s"]
                      }
                    ]
                  }
                }
                """.formatted(marker, status, totalTokens, status, casePassRate, caseId, caseStatus, marker, bucketField, bucket),
                StandardCharsets.UTF_8);
        Files.setLastModifiedTime(artifact, FileTime.from(updatedAt));
    }
}
