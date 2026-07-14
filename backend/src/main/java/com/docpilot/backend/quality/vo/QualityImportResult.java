package com.docpilot.backend.quality.vo;

public record QualityImportResult(
        int scanned,
        int imported,
        int updated,
        int skippedDuplicate,
        int rejected,
        int failed
) {
}
