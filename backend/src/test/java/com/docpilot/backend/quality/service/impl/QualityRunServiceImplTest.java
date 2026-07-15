package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.entity.QualityRun;
import com.docpilot.backend.quality.entity.QualityRunCase;
import com.docpilot.backend.quality.entity.QualityRunGate;
import com.docpilot.backend.quality.mapper.QualityRunCaseMapper;
import com.docpilot.backend.quality.mapper.QualityRunGateMapper;
import com.docpilot.backend.quality.mapper.QualityRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QualityRunServiceImplTest {

    private final QualityRunMapper runMapper = mock(QualityRunMapper.class);
    private final QualityRunGateMapper gateMapper = mock(QualityRunGateMapper.class);
    private final QualityRunCaseMapper caseMapper = mock(QualityRunCaseMapper.class);
    private final QualityRunServiceImpl service = new QualityRunServiceImpl(
            runMapper,
            gateMapper,
            caseMapper,
            new ObjectMapper()
    );

    @Test
    void shouldReadRunDetailFromPersistedSnapshot() {
        QualityRun run = run();
        when(runMapper.selectByMarker("docpilot-quality-db")).thenReturn(run);
        when(gateMapper.selectByRunId(7L)).thenReturn(List.of(gate()));
        when(caseMapper.selectByRunId(7L)).thenReturn(List.of(evalCase()));

        var detail = service.getRunDetail("docpilot-quality-db").orElseThrow();

        assertThat(detail.summary().marker()).isEqualTo("docpilot-quality-db");
        assertThat(detail.summary().tokenUsage().totalTokens()).isEqualTo(17);
        assertThat(detail.summary().environment()).isEqualTo("local");
        assertThat(detail.gates()).hasSize(1);
        assertThat(detail.gates().get(0).metrics()).containsEntry("casePassRate", 1.0);
        assertThat(detail.evalCases()).hasSize(1);
        assertThat(detail.evalCases().get(0).caseId()).isEqualTo("agent-rag-evidence-trace");
    }

    @Test
    void shouldBuildTrendFromPersistedRuns() {
        QualityRun run = run();
        when(runMapper.selectRecent(anyInt())).thenReturn(List.of(run));
        when(gateMapper.selectByRunId(7L)).thenReturn(List.of(gate()));
        when(caseMapper.selectByRunId(7L)).thenReturn(List.of(evalCase()));

        var trend = service.getTrendSummary(20);

        assertThat(trend.runCount()).isEqualTo(1);
        assertThat(trend.statusCounts()).containsEntry("PASS", 1);
        assertThat(trend.averageCasePassRate()).isEqualTo(1.0);
        assertThat(trend.totalTokens()).isEqualTo(17);
        assertThat(trend.averageLatencyMs()).isEqualTo(1250.5);
        assertThat(trend.averageDurationMs()).isEqualTo(60000.0);
        assertThat(trend.points()).hasSize(1);
        assertThat(trend.points().get(0).latencyMs()).isEqualTo(1250.5);
        assertThat(trend.points().get(0).durationMs()).isEqualTo(60000L);
    }

    @Test
    void shouldBuildDomainTrendsFromPersistedRuns() {
        QualityRun memoryRun = run("docpilot-memory-quality-db", 8L, "backend/target/memory-quality");
        QualityRun ragRun = run("docpilot-rerank-representative-db", 9L, "backend/target/rag-quality");
        when(runMapper.selectRecent(anyInt())).thenReturn(List.of(memoryRun, ragRun));
        when(gateMapper.selectByRunId(8L)).thenReturn(List.of(memoryGate()));
        when(caseMapper.selectByRunId(8L)).thenReturn(List.of());
        when(gateMapper.selectByRunId(9L)).thenReturn(List.of(ragRepresentativeGate()));
        when(caseMapper.selectByRunId(9L)).thenReturn(List.of());

        var trend = service.getTrendSummary(20);

        assertThat(trend.domainTrends()).containsKeys("memoryQuality", "ragRepresentativeEval");
        assertThat(trend.domainTrends().get("memoryQuality").latestMarker())
                .isEqualTo("docpilot-memory-quality-db");
        assertThat(trend.domainTrends().get("memoryQuality").latestMetrics())
                .containsEntry("extractedSuggestionCount", 3.0);
        assertThat(trend.domainTrends().get("memoryQuality").latestFlags())
                .containsEntry("conflictAcceptBlocked", true);
        assertThat(trend.domainTrends().get("ragRepresentativeEval").latestMetrics())
                .containsEntry("upliftCaseCount", 10.0);
        assertThat(trend.domainTrends().get("ragRepresentativeEval").latestFlags())
                .containsEntry("rerankApplied", true);
    }

    @Test
    void shouldHideReservedImportTestMarkersFromPersistedViews() {
        QualityRun hidden = run("docpilot-import-clean-3432907306300", 8L, "backend/target/agent-quality-eval");
        QualityRun visible = run("docpilot-agent-quality-eval-real", 9L, "backend/target/agent-quality-eval");
        when(runMapper.selectRecent(anyInt())).thenReturn(List.of(hidden, visible));
        when(gateMapper.selectByRunId(9L)).thenReturn(List.of(gate()));
        when(caseMapper.selectByRunId(9L)).thenReturn(List.of(evalCase()));

        var runs = service.listRecentRuns(1);
        var trend = service.getTrendSummary(1);
        var hiddenDetail = service.getRunDetail("docpilot-import-clean-3432907306300");

        assertThat(runs).extracting(item -> item.marker())
                .containsExactly("docpilot-agent-quality-eval-real");
        assertThat(trend.runCount()).isEqualTo(1);
        assertThat(trend.points()).extracting(point -> point.marker())
                .containsExactly("docpilot-agent-quality-eval-real");
        assertThat(hiddenDetail).isEmpty();
    }

    private QualityRun run() {
        return run("docpilot-quality-db", 7L, "backend/target/agent-quality-eval");
    }

    private QualityRun run(String marker, Long id, String sourceRoot) {
        QualityRun run = new QualityRun();
        run.setId(id);
        run.setMarker(marker);
        run.setStatus("PASS");
        run.setEnvironment("local");
        run.setDataSource("artifact_import");
        run.setSourceRootKey(sourceRoot);
        run.setSourceRelativePath(marker + "/artifact.json");
        run.setSourceSha256("a".repeat(64));
        run.setArtifactName(marker + "/artifact.json");
        run.setArtifactUpdatedAt(LocalDateTime.of(2026, 7, 14, 1, 2));
        run.setImportedAt(LocalDateTime.of(2026, 7, 14, 1, 3));
        run.setGateCount(1);
        run.setFailedGateCount(0);
        run.setReviewGateCount(0);
        run.setEvalCaseCount(1);
        run.setTraceReferenceCount(0);
        run.setTotalTokens(17);
        run.setEstimatedCost(new BigDecimal("0.01000000"));
        run.setFailureBucketsJson("[]");
        run.setReviewBucketsJson("[]");
        run.setDiagnosticsJson("""
                {
                  "runObservation": {
                    "suiteId": "agent_quality",
                    "coverageProfile": "runtime_full",
                    "durationMs": 60000,
                    "latencyMs": 1250.5,
                    "sampleGaps": []
                  }
                }
                """);
        run.setTraceReferencesJson("[]");
        run.setArtifactMissing(false);
        run.setArtifactParseFailed(false);
        run.setRedactionStatus("PASS");
        return run;
    }

    private QualityRunGate gate() {
        QualityRunGate gate = new QualityRunGate();
        gate.setRunId(7L);
        gate.setGateName("qualityConsoleHealth");
        gate.setStatus("PASS");
        gate.setPassed(true);
        gate.setMetricsJson("{\"casePassRate\":1.0}");
        gate.setFlagsJson("{\"traceLinked\":true}");
        gate.setFailureBucketsJson("[]");
        gate.setReviewBucketsJson("[]");
        gate.setSortOrder(0);
        return gate;
    }

    private QualityRunGate memoryGate() {
        QualityRunGate gate = new QualityRunGate();
        gate.setRunId(8L);
        gate.setGateName("memoryQuality");
        gate.setStatus("PASS");
        gate.setPassed(true);
        gate.setMetricsJson("{\"casePassRate\":1.0,\"extractedSuggestionCount\":3}");
        gate.setFlagsJson("{\"conflictAcceptBlocked\":true}");
        gate.setFailureBucketsJson("[]");
        gate.setReviewBucketsJson("[]");
        gate.setSortOrder(0);
        return gate;
    }

    private QualityRunGate ragRepresentativeGate() {
        QualityRunGate gate = new QualityRunGate();
        gate.setRunId(9L);
        gate.setGateName("rerankRepresentativeEval");
        gate.setStatus("PASS");
        gate.setPassed(true);
        gate.setMetricsJson("{\"caseCount\":12,\"targetCoveragePassCount\":10,\"noEvidenceCorrectCount\":2,\"upliftCaseCount\":10}");
        gate.setFlagsJson("{\"rerankApplied\":true}");
        gate.setFailureBucketsJson("[]");
        gate.setReviewBucketsJson("[]");
        gate.setSortOrder(0);
        return gate;
    }

    private QualityRunCase evalCase() {
        QualityRunCase item = new QualityRunCase();
        item.setRunId(7L);
        item.setCaseId("agent-rag-evidence-trace");
        item.setCaseType("rag");
        item.setStatus("PASS");
        item.setPassed(true);
        item.setTraceId("trace-agent-rag-evidence-trace");
        item.setAgentRunId("agent-run-agent-rag-evidence-trace");
        item.setMetricsJson("{\"casePassRate\":1.0}");
        item.setFlagsJson("{\"traceLinked\":true}");
        item.setFailureBucketsJson("[]");
        item.setReviewBucketsJson("[]");
        item.setSortOrder(0);
        return item;
    }
}
