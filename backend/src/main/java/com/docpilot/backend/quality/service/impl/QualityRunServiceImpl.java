package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.entity.QualityRun;
import com.docpilot.backend.quality.entity.QualityRunCase;
import com.docpilot.backend.quality.entity.QualityRunGate;
import com.docpilot.backend.quality.mapper.QualityRunCaseMapper;
import com.docpilot.backend.quality.mapper.QualityRunGateMapper;
import com.docpilot.backend.quality.mapper.QualityRunMapper;
import com.docpilot.backend.quality.service.QualityArtifactService;
import com.docpilot.backend.quality.vo.QualityEvalCaseResultDetail;
import com.docpilot.backend.quality.vo.QualityGateSummary;
import com.docpilot.backend.quality.vo.QualityRepeatedCaseSummary;
import com.docpilot.backend.quality.vo.QualityRunDetail;
import com.docpilot.backend.quality.vo.QualityRunDiagnostics;
import com.docpilot.backend.quality.vo.QualityRunSummary;
import com.docpilot.backend.quality.vo.QualityTokenUsageSummary;
import com.docpilot.backend.quality.vo.QualityTraceReference;
import com.docpilot.backend.quality.vo.QualityTrendPoint;
import com.docpilot.backend.quality.vo.QualityTrendSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Primary
@Service
public class QualityRunServiceImpl implements QualityArtifactService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Number>> NUMBER_MAP = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Boolean>> BOOLEAN_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<QualityTraceReference>> TRACE_REFERENCE_LIST = new TypeReference<>() {
    };
    private static final String INTERNAL_IMPORT_TEST_MARKER_PREFIX = "docpilot-import-";

    private final QualityRunMapper qualityRunMapper;
    private final QualityRunGateMapper qualityRunGateMapper;
    private final QualityRunCaseMapper qualityRunCaseMapper;
    private final ObjectMapper objectMapper;
    private final QualityDomainTrendAssembler domainTrendAssembler;

    public QualityRunServiceImpl(
            QualityRunMapper qualityRunMapper,
            QualityRunGateMapper qualityRunGateMapper,
            QualityRunCaseMapper qualityRunCaseMapper,
            ObjectMapper objectMapper) {
        this.qualityRunMapper = qualityRunMapper;
        this.qualityRunGateMapper = qualityRunGateMapper;
        this.qualityRunCaseMapper = qualityRunCaseMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.domainTrendAssembler = new QualityDomainTrendAssembler();
    }

    @Override
    public List<QualityRunSummary> listRecentRuns(int limit) {
        int resolvedLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 200);
        return selectRecentVisibleRuns(resolvedLimit).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public Optional<QualityRunDetail> getRunDetail(String marker) {
        if (marker == null || marker.isBlank()) {
            return Optional.empty();
        }
        if (isInternalImportTestMarker(marker)) {
            return Optional.empty();
        }
        QualityRun run = qualityRunMapper.selectByMarker(marker.trim());
        if (run == null) {
            return Optional.empty();
        }
        return Optional.of(toDetail(run));
    }

    @Override
    public QualityTrendSummary getTrendSummary(int limit) {
        int resolvedLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 200);
        List<QualityRunDetail> details = selectRecentVisibleRuns(resolvedLimit).stream()
                .map(this::toDetail)
                .toList();
        return buildTrend(details, resolvedLimit);
    }

    private List<QualityRun> selectRecentVisibleRuns(int resolvedLimit) {
        int scanLimit = Math.min(500, Math.max(resolvedLimit, resolvedLimit * 5));
        return qualityRunMapper.selectRecent(scanLimit).stream()
                .filter(run -> run != null && !isInternalImportTestMarker(run.getMarker()))
                .limit(resolvedLimit)
                .toList();
    }

    private QualityRunDetail toDetail(QualityRun run) {
        List<QualityGateSummary> gates = qualityRunGateMapper.selectByRunId(run.getId()).stream()
                .map(this::toGateSummary)
                .toList();
        List<QualityEvalCaseResultDetail> evalCases = qualityRunCaseMapper.selectByRunId(run.getId()).stream()
                .map(this::toEvalCase)
                .toList();
        return new QualityRunDetail(
                toSummary(run),
                gates,
                evalCases,
                readTraceReferences(run.getTraceReferencesJson()),
                readDiagnostics(run.getDiagnosticsJson())
        );
    }

    private QualityRunSummary toSummary(QualityRun run) {
        return new QualityRunSummary(
                safe(run.getMarker()),
                safe(run.getSourceRootKey()),
                safe(run.getArtifactName()),
                safeStatus(run.getStatus()),
                toInstant(run.getArtifactUpdatedAt(), run.getImportedAt()),
                intValue(run.getGateCount()),
                intValue(run.getFailedGateCount()),
                intValue(run.getReviewGateCount()),
                readStringList(run.getFailureBucketsJson()),
                readStringList(run.getReviewBucketsJson()),
                new QualityTokenUsageSummary(
                        run.getPromptTokens(),
                        run.getCompletionTokens(),
                        run.getTotalTokens(),
                        run.getEstimatedCost()),
                Boolean.TRUE.equals(run.getArtifactMissing()),
                Boolean.TRUE.equals(run.getArtifactParseFailed()),
                safe(run.getEnvironment()),
                safe(run.getDataSource()),
                toInstant(run.getImportedAt(), null)
        );
    }

    private QualityGateSummary toGateSummary(QualityRunGate gate) {
        return new QualityGateSummary(
                safe(gate.getGateName()),
                safeStatus(gate.getStatus()),
                gate.getPassed(),
                readNumberMap(gate.getMetricsJson()),
                readBooleanMap(gate.getFlagsJson()),
                readStringList(gate.getFailureBucketsJson()),
                readStringList(gate.getReviewBucketsJson())
        );
    }

    private QualityEvalCaseResultDetail toEvalCase(QualityRunCase item) {
        return new QualityEvalCaseResultDetail(
                safe(item.getCaseId()),
                safe(item.getCaseType()),
                safeStatus(item.getStatus()),
                item.getPassed(),
                safe(item.getTraceId()),
                safe(item.getAgentRunId()),
                readStringList(item.getFailureBucketsJson()),
                readStringList(item.getReviewBucketsJson()),
                readNumberMap(item.getMetricsJson()),
                readBooleanMap(item.getFlagsJson())
        );
    }

    private QualityTrendSummary buildTrend(List<QualityRunDetail> details, int limit) {
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        Map<String, Integer> failureBucketCounts = new LinkedHashMap<>();
        Map<String, Integer> reviewBucketCounts = new LinkedHashMap<>();
        List<QualityTrendPoint> points = new ArrayList<>();
        Map<String, CaseCounter> caseCounters = new LinkedHashMap<>();
        int totalTokens = 0;
        boolean hasTokens = false;
        BigDecimal estimatedCost = BigDecimal.ZERO;
        boolean hasCost = false;
        double passRateSum = 0.0;
        int passRateCount = 0;
        double latencySum = 0.0;
        int latencyCount = 0;
        double durationSum = 0.0;
        int durationCount = 0;

        for (QualityRunDetail detail : details) {
            QualityRunSummary summary = detail.summary();
            increment(statusCounts, summary.status());
            summary.failureBuckets().forEach(bucket -> increment(failureBucketCounts, bucket));
            summary.reviewBuckets().forEach(bucket -> increment(reviewBucketCounts, bucket));
            detail.evalCases().forEach(item -> {
                item.failureBuckets().forEach(bucket -> increment(failureBucketCounts, bucket));
                item.reviewBuckets().forEach(bucket -> increment(reviewBucketCounts, bucket));
                if (!item.failureBuckets().isEmpty() || safeStatus(item.status()).startsWith("FAILED")) {
                    caseCounters.computeIfAbsent(item.caseId(), CaseCounter::new).failed(summary.marker(), item.status());
                } else if (!item.reviewBuckets().isEmpty() || "REVIEW".equals(safeStatus(item.status()))) {
                    caseCounters.computeIfAbsent(item.caseId(), CaseCounter::new).review(summary.marker(), item.status());
                }
            });
            Double casePassRate = findMetric(detail, "casePassRate");
            if (casePassRate == null) {
                casePassRate = derivedCasePassRate(detail.evalCases());
            }
            if (casePassRate != null) {
                passRateSum += casePassRate;
                passRateCount++;
            }
            Double latencyMs = runLatencyMs(detail);
            if (latencyMs != null) {
                latencySum += latencyMs;
                latencyCount++;
            }
            Double durationMs = runDurationMs(detail);
            if (durationMs != null) {
                durationSum += durationMs;
                durationCount++;
            }
            Integer runTokens = summary.tokenUsage().totalTokens();
            if (runTokens != null) {
                totalTokens += runTokens;
                hasTokens = true;
            }
            if (summary.tokenUsage().estimatedCost() != null) {
                estimatedCost = estimatedCost.add(summary.tokenUsage().estimatedCost());
                hasCost = true;
            }
            points.add(new QualityTrendPoint(
                    summary.marker(),
                    summary.status(),
                    summary.updatedAt(),
                    summary.failedGateCount(),
                    summary.reviewGateCount(),
                    casePassRate,
                    runTokens,
                    summary.tokenUsage().estimatedCost() == null ? null : summary.tokenUsage().estimatedCost().doubleValue(),
                    latencyMs,
                    durationMs,
                    summary.failureBuckets(),
                    summary.reviewBuckets()
            ));
        }

        return new QualityTrendSummary(
                limit,
                details.size(),
                statusCounts,
                failureBucketCounts,
                reviewBucketCounts,
                average(passRateSum, passRateCount),
                hasTokens ? totalTokens : null,
                hasCost ? estimatedCost.doubleValue() : null,
                average(latencySum, latencyCount),
                average(durationSum, durationCount),
                caseCounters.values().stream()
                        .filter(CaseCounter::repeated)
                        .sorted(Comparator.comparingInt(CaseCounter::total).reversed())
                        .map(CaseCounter::toSummary)
                        .toList(),
                points,
                domainTrendAssembler.build(details)
        );
    }

    private Double derivedCasePassRate(List<QualityEvalCaseResultDetail> evalCases) {
        if (evalCases.isEmpty()) {
            return null;
        }
        long passed = evalCases.stream().filter(item -> Boolean.TRUE.equals(item.passed())).count();
        return (double) passed / evalCases.size();
    }

    private Double findMetric(QualityRunDetail detail, String name) {
        String expected = normalize(name);
        for (QualityGateSummary gate : detail.gates()) {
            for (Map.Entry<String, Number> entry : gate.metrics().entrySet()) {
                if (expected.equals(normalize(entry.getKey()))) {
                    return entry.getValue().doubleValue();
                }
            }
        }
        for (QualityEvalCaseResultDetail item : detail.evalCases()) {
            for (Map.Entry<String, Number> entry : item.metrics().entrySet()) {
                if (expected.equals(normalize(entry.getKey()))) {
                    return entry.getValue().doubleValue();
                }
            }
        }
        return null;
    }

    private Double runLatencyMs(QualityRunDetail detail) {
        return detail.diagnostics().runObservation().latencyMs();
    }

    private Double runDurationMs(QualityRunDetail detail) {
        Long observed = detail.diagnostics().runObservation().durationMs();
        return observed == null ? null : observed.doubleValue();
    }

    private Double sumMetric(QualityRunDetail detail, String name) {
        String expected = normalize(name);
        double sum = 0.0;
        boolean found = false;
        for (QualityGateSummary gate : detail.gates()) {
            for (Map.Entry<String, Number> entry : gate.metrics().entrySet()) {
                if (expected.equals(normalize(entry.getKey()))) {
                    sum += entry.getValue().doubleValue();
                    found = true;
                }
            }
        }
        for (QualityEvalCaseResultDetail item : detail.evalCases()) {
            for (Map.Entry<String, Number> entry : item.metrics().entrySet()) {
                if (expected.equals(normalize(entry.getKey()))) {
                    sum += entry.getValue().doubleValue();
                    found = true;
                }
            }
        }
        return found ? sum : null;
    }

    private void increment(Map<String, Integer> counts, String key) {
        counts.merge(key == null || key.isBlank() ? "UNKNOWN" : key, 1, Integer::sum);
    }

    private Double average(double sum, int count) {
        return count <= 0 ? null : sum / count;
    }

    private List<String> readStringList(String json) {
        return read(json, STRING_LIST, List.of()).stream()
                .filter(item -> item != null && !item.isBlank())
                .toList();
    }

    private Map<String, Number> readNumberMap(String json) {
        return read(json, NUMBER_MAP, Map.of());
    }

    private Map<String, Boolean> readBooleanMap(String json) {
        return read(json, BOOLEAN_MAP, Map.of());
    }

    private List<QualityTraceReference> readTraceReferences(String json) {
        return read(json, TRACE_REFERENCE_LIST, List.of());
    }

    private QualityRunDiagnostics readDiagnostics(String json) {
        return read(json, QualityRunDiagnostics.class, QualityRunDiagnostics.empty());
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private <T> T read(String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private Instant toInstant(LocalDateTime primary, LocalDateTime fallback) {
        LocalDateTime value = primary == null ? fallback : primary;
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeStatus(String value) {
        String status = safe(value).toUpperCase(java.util.Locale.ROOT);
        return status.isBlank() ? "REVIEW" : status;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isInternalImportTestMarker(String marker) {
        return marker != null && marker.trim().startsWith(INTERNAL_IMPORT_TEST_MARKER_PREFIX);
    }

    private static final class CaseCounter {
        private final String caseId;
        private int failedCount;
        private int reviewCount;
        private String latestStatus = "";
        private String latestRunMarker = "";

        private CaseCounter(String caseId) {
            this.caseId = caseId == null ? "" : caseId;
        }

        private void failed(String marker, String status) {
            failedCount++;
            latestRunMarker = marker == null ? "" : marker;
            latestStatus = status == null ? "" : status;
        }

        private void review(String marker, String status) {
            reviewCount++;
            latestRunMarker = marker == null ? "" : marker;
            latestStatus = status == null ? "" : status;
        }

        private boolean repeated() {
            return total() > 0 && !caseId.isBlank();
        }

        private int total() {
            return failedCount + reviewCount;
        }

        private QualityRepeatedCaseSummary toSummary() {
            return new QualityRepeatedCaseSummary(caseId, failedCount, reviewCount, latestStatus, latestRunMarker);
        }
    }

}
