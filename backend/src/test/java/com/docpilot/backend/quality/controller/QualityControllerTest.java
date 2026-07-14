package com.docpilot.backend.quality.controller;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.quality.service.QualityArtifactImportService;
import com.docpilot.backend.quality.service.QualityArtifactService;
import com.docpilot.backend.quality.service.QualityConsoleAccessGuard;
import com.docpilot.backend.quality.service.QualityEvalCatalogService;
import com.docpilot.backend.quality.vo.QualityConsoleStatus;
import com.docpilot.backend.quality.vo.QualityEvalCaseCatalogItem;
import com.docpilot.backend.quality.vo.QualityImportResult;
import com.docpilot.backend.quality.vo.QualityRunDetail;
import com.docpilot.backend.quality.vo.QualityRunSummary;
import com.docpilot.backend.quality.vo.QualityTokenUsageSummary;
import com.docpilot.backend.quality.vo.QualityTrendSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityControllerTest {

    private final QualityArtifactService qualityArtifactService = mock(QualityArtifactService.class);
    private final QualityEvalCatalogService qualityEvalCatalogService = mock(QualityEvalCatalogService.class);
    private final QualityArtifactImportService qualityArtifactImportService = mock(QualityArtifactImportService.class);
    private final QualityConsoleAccessGuard qualityConsoleAccessGuard = mock(QualityConsoleAccessGuard.class);

    @Test
    void shouldRejectWhenConsoleDisabled() {
        QualityController controller = controller();
        when(qualityConsoleAccessGuard.requireInternalAdmin())
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "quality console is disabled"));

        assertThatThrownBy(() -> controller.listRuns(null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void shouldRequireLoginContext() {
        QualityController controller = controller();
        when(qualityConsoleAccessGuard.requireInternalAdmin())
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录"));

        assertThatThrownBy(() -> controller.listRuns(null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void shouldReturnConsoleStatusWithoutLoadingRuns() {
        QualityController controller = controller();
        when(qualityConsoleAccessGuard.currentStatus()).thenReturn(new QualityConsoleStatus(
                true,
                true,
                "OK",
                "DB",
                3,
                Instant.parse("2026-07-14T00:00:00Z"),
                "local"
        ));

        var response = controller.status();

        assertThat(response.data().authorized()).isTrue();
        assertThat(response.data().runCount()).isEqualTo(3);
        verify(qualityConsoleAccessGuard).currentStatus();
    }

    @Test
    void shouldListRecentRunsWithDefaultLimit() {
        QualityController controller = controller();
        when(qualityConsoleAccessGuard.requireInternalAdmin()).thenReturn(7L);
        when(qualityArtifactService.listRecentRuns(QualityArtifactService.DEFAULT_LIMIT))
                .thenReturn(List.of(summary("docpilot-quality-api")));

        var response = controller.listRuns(null);

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).marker()).isEqualTo("docpilot-quality-api");
        verify(qualityConsoleAccessGuard).requireInternalAdmin();
        verify(qualityArtifactService).listRecentRuns(QualityArtifactService.DEFAULT_LIMIT);
    }

    @Test
    void shouldListRecentRunsWithCustomLimit() {
        QualityController controller = controller();
        when(qualityConsoleAccessGuard.requireInternalAdmin()).thenReturn(7L);
        when(qualityArtifactService.listRecentRuns(3)).thenReturn(List.of());

        controller.listRuns(3);

        verify(qualityArtifactService).listRecentRuns(3);
    }

    @Test
    void shouldReturnRunDetailByMarker() {
        QualityController controller = controller();
        when(qualityConsoleAccessGuard.requireInternalAdmin()).thenReturn(7L);
        QualityRunDetail detail = new QualityRunDetail(summary("docpilot-quality-detail"), List.of(), List.of());
        when(qualityArtifactService.getRunDetail("docpilot-quality-detail")).thenReturn(Optional.of(detail));

        var response = controller.detail("docpilot-quality-detail");

        assertThat(response.data().summary().marker()).isEqualTo("docpilot-quality-detail");
        verify(qualityArtifactService).getRunDetail("docpilot-quality-detail");
    }

    @Test
    void shouldFailWhenRunDetailMissing() {
        QualityController controller = controller();
        when(qualityConsoleAccessGuard.requireInternalAdmin()).thenReturn(7L);
        when(qualityArtifactService.getRunDetail("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.detail("missing"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void shouldReturnEvalCaseCatalog() {
        QualityController controller = controller();
        when(qualityConsoleAccessGuard.requireInternalAdmin()).thenReturn(7L);
        when(qualityEvalCatalogService.listEvalCases()).thenReturn(List.of(new QualityEvalCaseCatalogItem(
                "agent-rag-evidence-trace",
                1,
                "quality-console",
                "2026-07-05",
                "P1",
                "agent_rag_trace",
                "FAILED_CORE_FLOW",
                List.of("evidence_required", "trace_required"),
                List.of("quality_tests"),
                List.of("marker=docpilot-agent-quality-eval:status=PASS:issue=NONE"),
                List.of("REA-20260703-P1-001"),
                "docpilot-cloud-quality-20260703213703-dbef08",
                List.of("check_short_document_chunk"),
                "rag",
                List.of("rag", "trace"),
                List.of("ragEvidence"),
                List.of("rag_qa_tool"),
                List.of("requireTraceLink=true"),
                "PASS",
                "docpilot-agent-quality-eval",
                "trace-agent-rag-evidence-trace",
                "agent-run-agent-rag-evidence-trace",
                List.of(),
                List.of()
        )));

        var response = controller.listEvalCases();

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).caseId()).isEqualTo("agent-rag-evidence-trace");
        assertThat(response.data().get(0).sourceIssueIds()).containsExactly("REA-20260703-P1-001");
        assertThat(response.data().get(0).lastVerifiedMarker()).isEqualTo("docpilot-cloud-quality-20260703213703-dbef08");
        verify(qualityEvalCatalogService).listEvalCases();
    }

    @Test
    void shouldReturnTrendSummary() {
        QualityController controller = controller();
        when(qualityConsoleAccessGuard.requireInternalAdmin()).thenReturn(7L);
        when(qualityArtifactService.getTrendSummary(5)).thenReturn(new QualityTrendSummary(
                5,
                1,
                java.util.Map.of("PASS", 1),
                java.util.Map.of(),
                java.util.Map.of(),
                1.0,
                17,
                0.01,
                120.0,
                140.0,
                List.of(),
                List.of()
        ));

        var response = controller.trends(5);

        assertThat(response.data().runCount()).isEqualTo(1);
        assertThat(response.data().totalTokens()).isEqualTo(17);
        verify(qualityArtifactService).getTrendSummary(5);
    }

    @Test
    void shouldImportArtifactsWithInternalAdmin() {
        QualityController controller = controller();
        when(qualityConsoleAccessGuard.requireInternalAdmin()).thenReturn(7L);
        when(qualityArtifactImportService.importRecentArtifacts(10, 7L))
                .thenReturn(new QualityImportResult(10, 8, 1, 1, 0, 0));

        var response = controller.importArtifacts(10);

        assertThat(response.data().imported()).isEqualTo(8);
        verify(qualityConsoleAccessGuard).requireInternalAdmin();
        verify(qualityArtifactImportService).importRecentArtifacts(10, 7L);
    }

    private QualityController controller() {
        return new QualityController(
                qualityArtifactService,
                qualityEvalCatalogService,
                qualityArtifactImportService,
                qualityConsoleAccessGuard
        );
    }

    private QualityRunSummary summary(String marker) {
        return new QualityRunSummary(
                marker,
                "backend/target/audit",
                marker + "/artifact.json",
                "PASS",
                Instant.parse("2026-07-04T00:00:00Z"),
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
