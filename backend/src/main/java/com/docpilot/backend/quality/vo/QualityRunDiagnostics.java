package com.docpilot.backend.quality.vo;

import java.util.List;

public record QualityRunDiagnostics(
        RunObservationSummary runObservation,
        DocumentCoverageSummary documentCoverage,
        ToolQualitySummary toolQuality,
        MemoryQualitySummary memoryQuality,
        ParserQualitySummary parserQuality
) {

    public QualityRunDiagnostics {
        runObservation = runObservation == null ? RunObservationSummary.empty() : runObservation;
        documentCoverage = documentCoverage == null ? DocumentCoverageSummary.empty() : documentCoverage;
        toolQuality = toolQuality == null ? ToolQualitySummary.empty() : toolQuality;
        memoryQuality = memoryQuality == null ? MemoryQualitySummary.empty() : memoryQuality;
        parserQuality = parserQuality == null ? ParserQualitySummary.empty() : parserQuality;
    }

    public static QualityRunDiagnostics empty() {
        return new QualityRunDiagnostics(
                RunObservationSummary.empty(),
                DocumentCoverageSummary.empty(),
                ToolQualitySummary.empty(),
                MemoryQualitySummary.empty(),
                ParserQualitySummary.empty()
        );
    }

    public record RunObservationSummary(
            Integer schemaVersion,
            String suiteId,
            String suiteVersion,
            String coverageProfile,
            String startedAt,
            String finishedAt,
            Long durationMs,
            Double latencyMs,
            List<String> sampleGaps
    ) {

        public RunObservationSummary {
            suiteId = safeText(suiteId);
            suiteVersion = safeText(suiteVersion);
            coverageProfile = safeText(coverageProfile);
            startedAt = safeText(startedAt);
            finishedAt = safeText(finishedAt);
            sampleGaps = sampleGaps == null ? List.of() : List.copyOf(sampleGaps);
        }

        public static RunObservationSummary empty() {
            return new RunObservationSummary(null, "", "", "", "", "", null, null, List.of());
        }

        private static String safeText(String value) {
            return value == null ? "" : value.trim();
        }
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

    public record ParserQualitySummary(
            Integer expectedFileTypeCount,
            Integer coveredFileTypeCount,
            Integer missingFileTypeCount,
            Boolean allFileTypesCovered,
            Integer expectedStructureSignalCount,
            Integer coveredStructureSignalCount,
            Integer missingStructureSignalCount,
            Boolean allStructureSignalsCovered,
            Integer fileCount,
            Integer parsedFileCount,
            Integer parserFailureCount,
            Double parsePassRate,
            Integer sourceLocatorCount,
            Double sourceLocatorCoverageRate,
            Integer chunkCountKnown,
            Integer chunkCount,
            Integer retrieveHitCount,
            Integer directRetrieveHitCount,
            Integer qaRetrievalHitCount,
            Integer citationCount,
            Integer directRetrieveOkCount,
            Integer qaRetrieveOkCount,
            Integer directRetrieveNoEvidenceCount,
            Integer qaRetrieveNoEvidenceCount,
            Integer directRetrieveMaxAttempts,
            Integer qaRetrieveMaxAttempts,
            Boolean environmentUnstable,
            Double retrieveCoverageRate,
            Double citationCoverageRate,
            Integer negativeCaseCount,
            Integer negativeCasePassCount,
            Integer negativeCaseFailCount,
            Double boundaryPassRate,
            Boolean unsupportedUploadRejected,
            Integer warningCountKnown,
            Integer totalWarningCount,
            Integer filesWithWarnings,
            List<String> reviewReasons,
            List<String> unavailableMetrics
    ) {

        public ParserQualitySummary {
            reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
            unavailableMetrics = unavailableMetrics == null ? List.of() : List.copyOf(unavailableMetrics);
        }

        public static ParserQualitySummary empty() {
            return new ParserQualitySummary(
                    null, null, null, null,
                    null, null, null, null,
                    null, null, null, null,
                    null, null,
                    null, null, null, null,
                    null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null,
                    null, null, null, null,
                    List.of(), List.of()
            );
        }
    }
}
