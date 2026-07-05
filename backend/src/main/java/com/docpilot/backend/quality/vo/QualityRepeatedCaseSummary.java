package com.docpilot.backend.quality.vo;

public record QualityRepeatedCaseSummary(
        String caseId,
        int failedCount,
        int reviewCount,
        String latestStatus,
        String latestRunMarker
) {

    public QualityRepeatedCaseSummary {
        caseId = caseId == null ? "" : caseId.trim();
        latestStatus = latestStatus == null ? "" : latestStatus.trim();
        latestRunMarker = latestRunMarker == null ? "" : latestRunMarker.trim();
    }
}
