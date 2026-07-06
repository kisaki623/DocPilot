package com.docpilot.backend.quality.vo;

public record QualityRunDiagnostics(
        DocumentCoverageSummary documentCoverage,
        ToolQualitySummary toolQuality,
        MemoryQualitySummary memoryQuality
) {

    public QualityRunDiagnostics {
        documentCoverage = documentCoverage == null ? DocumentCoverageSummary.empty() : documentCoverage;
        toolQuality = toolQuality == null ? ToolQualitySummary.empty() : toolQuality;
        memoryQuality = memoryQuality == null ? MemoryQualitySummary.empty() : memoryQuality;
    }

    public static QualityRunDiagnostics empty() {
        return new QualityRunDiagnostics(
                DocumentCoverageSummary.empty(),
                ToolQualitySummary.empty(),
                MemoryQualitySummary.empty()
        );
    }

    public record DocumentCoverageSummary(
            Integer documentCount,
            Integer coveredDocumentCount,
            Integer zeroHitDocumentCount,
            Integer maxHitsPerDocument,
            Integer minHitsPerDocument
    ) {

        public static DocumentCoverageSummary empty() {
            return new DocumentCoverageSummary(null, null, null, null, null);
        }
    }

    public record ToolQualitySummary(
            Integer toolCallCount,
            Integer toolFailureCount,
            Integer toolArgsReviewCount
    ) {

        public static ToolQualitySummary empty() {
            return new ToolQualitySummary(null, null, null);
        }
    }

    public record MemoryQualitySummary(
            Integer memoryTriggerCount,
            Integer memoryHitCount,
            Integer memoryReviewCount,
            Integer ragEvidenceCount
    ) {

        public static MemoryQualitySummary empty() {
            return new MemoryQualitySummary(null, null, null, null);
        }
    }
}
