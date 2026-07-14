package com.docpilot.backend.quality.controller;

import com.docpilot.backend.common.api.ApiResponse;
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
import com.docpilot.backend.quality.vo.QualityTrendSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quality")
public class QualityController {

    private final QualityArtifactService qualityArtifactService;
    private final QualityEvalCatalogService qualityEvalCatalogService;
    private final QualityArtifactImportService qualityArtifactImportService;
    private final QualityConsoleAccessGuard qualityConsoleAccessGuard;

    public QualityController(
            QualityArtifactService qualityArtifactService,
            QualityEvalCatalogService qualityEvalCatalogService,
            QualityArtifactImportService qualityArtifactImportService,
            QualityConsoleAccessGuard qualityConsoleAccessGuard) {
        this.qualityArtifactService = qualityArtifactService;
        this.qualityEvalCatalogService = qualityEvalCatalogService;
        this.qualityArtifactImportService = qualityArtifactImportService;
        this.qualityConsoleAccessGuard = qualityConsoleAccessGuard;
    }

    @GetMapping("/status")
    public ApiResponse<QualityConsoleStatus> status() {
        return ApiResponse.success(qualityConsoleAccessGuard.currentStatus());
    }

    @GetMapping("/runs")
    public ApiResponse<List<QualityRunSummary>> listRuns(
            @RequestParam(value = "limit", required = false) Integer limit) {
        qualityConsoleAccessGuard.requireInternalAdmin();
        int resolvedLimit = normalizeLimit(limit);
        return ApiResponse.success(qualityArtifactService.listRecentRuns(resolvedLimit));
    }

    @GetMapping("/runs/{marker}")
    public ApiResponse<QualityRunDetail> detail(@PathVariable("marker") String marker) {
        qualityConsoleAccessGuard.requireInternalAdmin();
        return ApiResponse.success(qualityArtifactService.getRunDetail(marker)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "quality run not found")));
    }

    @GetMapping("/eval-cases")
    public ApiResponse<List<QualityEvalCaseCatalogItem>> listEvalCases() {
        qualityConsoleAccessGuard.requireInternalAdmin();
        return ApiResponse.success(qualityEvalCatalogService.listEvalCases());
    }

    @GetMapping("/trends")
    public ApiResponse<QualityTrendSummary> trends(
            @RequestParam(value = "limit", required = false) Integer limit) {
        qualityConsoleAccessGuard.requireInternalAdmin();
        int resolvedLimit = normalizeLimit(limit);
        return ApiResponse.success(qualityArtifactService.getTrendSummary(resolvedLimit));
    }

    @PostMapping("/imports/artifacts")
    public ApiResponse<QualityImportResult> importArtifacts(
            @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = qualityConsoleAccessGuard.requireInternalAdmin();
        int resolvedLimit = limit == null ? QualityArtifactImportService.DEFAULT_IMPORT_LIMIT : Math.min(Math.max(limit, 1), 500);
        return ApiResponse.success(qualityArtifactImportService.importRecentArtifacts(resolvedLimit, userId));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return QualityArtifactService.DEFAULT_LIMIT;
        }
        return Math.min(limit, 100);
    }
}
