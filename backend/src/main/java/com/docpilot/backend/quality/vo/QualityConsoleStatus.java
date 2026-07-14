package com.docpilot.backend.quality.vo;

import java.time.Instant;

public record QualityConsoleStatus(
        boolean enabled,
        boolean authorized,
        String reason,
        String dataMode,
        int runCount,
        Instant lastImportedAt,
        String environment
) {

    public QualityConsoleStatus {
        reason = reason == null ? "" : reason.trim();
        dataMode = dataMode == null || dataMode.isBlank() ? "DB" : dataMode.trim();
        environment = environment == null || environment.isBlank() ? "local" : environment.trim();
    }
}
