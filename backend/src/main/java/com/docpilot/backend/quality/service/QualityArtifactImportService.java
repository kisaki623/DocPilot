package com.docpilot.backend.quality.service;

import com.docpilot.backend.quality.vo.QualityImportResult;

public interface QualityArtifactImportService {

    int DEFAULT_IMPORT_LIMIT = 200;

    QualityImportResult importRecentArtifacts(int limit, Long requestedByUserId);
}
