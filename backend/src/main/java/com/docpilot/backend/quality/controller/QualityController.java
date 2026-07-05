package com.docpilot.backend.quality.controller;

import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.quality.service.QualityArtifactService;
import com.docpilot.backend.quality.service.QualityEvalCatalogService;
import com.docpilot.backend.quality.vo.QualityEvalCaseCatalogItem;
import com.docpilot.backend.quality.vo.QualityRunDetail;
import com.docpilot.backend.quality.vo.QualityRunSummary;
import com.docpilot.backend.quality.vo.QualityTrendSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quality")
public class QualityController {

    private final QualityArtifactService qualityArtifactService;
    private final QualityEvalCatalogService qualityEvalCatalogService;
    private final boolean consoleEnabled;

    public QualityController(
            QualityArtifactService qualityArtifactService,
            QualityEvalCatalogService qualityEvalCatalogService,
            @Value("${app.quality.console.enabled:false}") boolean consoleEnabled) {
        this.qualityArtifactService = qualityArtifactService;
        this.qualityEvalCatalogService = qualityEvalCatalogService;
        this.consoleEnabled = consoleEnabled;
    }

    @GetMapping("/runs")
    public ApiResponse<List<QualityRunSummary>> listRuns(
            @RequestParam(value = "limit", required = false) Integer limit) {
        requireInternalAccess();
        int resolvedLimit = limit == null ? QualityArtifactService.DEFAULT_LIMIT : limit;
        return ApiResponse.success(qualityArtifactService.listRecentRuns(resolvedLimit));
    }

    @GetMapping("/runs/{marker}")
    public ApiResponse<QualityRunDetail> detail(@PathVariable("marker") String marker) {
        requireInternalAccess();
        return ApiResponse.success(qualityArtifactService.getRunDetail(marker)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "quality run not found")));
    }

    @GetMapping("/eval-cases")
    public ApiResponse<List<QualityEvalCaseCatalogItem>> listEvalCases() {
        requireInternalAccess();
        return ApiResponse.success(qualityEvalCatalogService.listEvalCases());
    }

    @GetMapping("/trends")
    public ApiResponse<QualityTrendSummary> trends(
            @RequestParam(value = "limit", required = false) Integer limit) {
        requireInternalAccess();
        int resolvedLimit = limit == null ? QualityArtifactService.DEFAULT_LIMIT : limit;
        return ApiResponse.success(qualityArtifactService.getTrendSummary(resolvedLimit));
    }

    private void requireInternalAccess() {
        if (!consoleEnabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "quality console is disabled");
        }
        UserHolder.requireUserId();
    }
}
