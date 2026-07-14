package com.docpilot.backend.quality.service;

import com.docpilot.backend.quality.vo.QualityRunDetail;
import com.docpilot.backend.quality.vo.QualityRunSummary;
import com.docpilot.backend.quality.vo.QualityTrendSummary;

import java.util.List;
import java.util.Optional;

public interface QualityArtifactService {

    int DEFAULT_LIMIT = 20;

    List<QualityRunSummary> listRecentRuns(int limit);

    Optional<QualityRunDetail> getRunDetail(String marker);

    QualityTrendSummary getTrendSummary(int limit);
}
