package com.docpilot.backend.quality.controller;

import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.quality.service.QualityArtifactService;
import com.docpilot.backend.quality.service.QualityEvalCatalogService;
import com.docpilot.backend.quality.vo.QualityEvalCaseCatalogItem;
import com.docpilot.backend.quality.vo.QualityRunDetail;
import com.docpilot.backend.quality.vo.QualityRunSummary;
import com.docpilot.backend.quality.vo.QualityTokenUsageSummary;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void shouldRejectWhenConsoleDisabled() {
        UserHolder.setUserId(7L);
        QualityController controller = new QualityController(qualityArtifactService, qualityEvalCatalogService, false);

        assertThatThrownBy(() -> controller.listRuns(null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void shouldRequireLoginContext() {
        QualityController controller = new QualityController(qualityArtifactService, qualityEvalCatalogService, true);

        assertThatThrownBy(() -> controller.listRuns(null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void shouldListRecentRunsWithDefaultLimit() {
        UserHolder.setUserId(7L);
        QualityController controller = new QualityController(qualityArtifactService, qualityEvalCatalogService, true);
        when(qualityArtifactService.listRecentRuns(QualityArtifactService.DEFAULT_LIMIT))
                .thenReturn(List.of(summary("docpilot-quality-api")));

        var response = controller.listRuns(null);

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).marker()).isEqualTo("docpilot-quality-api");
        verify(qualityArtifactService).listRecentRuns(QualityArtifactService.DEFAULT_LIMIT);
    }

    @Test
    void shouldListRecentRunsWithCustomLimit() {
        UserHolder.setUserId(7L);
        QualityController controller = new QualityController(qualityArtifactService, qualityEvalCatalogService, true);
        when(qualityArtifactService.listRecentRuns(3)).thenReturn(List.of());

        controller.listRuns(3);

        verify(qualityArtifactService).listRecentRuns(3);
    }

    @Test
    void shouldReturnRunDetailByMarker() {
        UserHolder.setUserId(7L);
        QualityController controller = new QualityController(qualityArtifactService, qualityEvalCatalogService, true);
        QualityRunDetail detail = new QualityRunDetail(summary("docpilot-quality-detail"), List.of(), List.of());
        when(qualityArtifactService.getRunDetail("docpilot-quality-detail")).thenReturn(Optional.of(detail));

        var response = controller.detail("docpilot-quality-detail");

        assertThat(response.data().summary().marker()).isEqualTo("docpilot-quality-detail");
        verify(qualityArtifactService).getRunDetail("docpilot-quality-detail");
    }

    @Test
    void shouldFailWhenRunDetailMissing() {
        UserHolder.setUserId(7L);
        QualityController controller = new QualityController(qualityArtifactService, qualityEvalCatalogService, true);
        when(qualityArtifactService.getRunDetail("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.detail("missing"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void shouldReturnEvalCaseCatalog() {
        UserHolder.setUserId(7L);
        QualityController controller = new QualityController(qualityArtifactService, qualityEvalCatalogService, true);
        when(qualityEvalCatalogService.listEvalCases()).thenReturn(List.of(new QualityEvalCaseCatalogItem(
                "agent-rag-evidence-trace",
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
        verify(qualityEvalCatalogService).listEvalCases();
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
