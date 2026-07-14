package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.vo.QualityDomainTrendPoint;
import com.docpilot.backend.quality.vo.QualityDomainTrendSummary;
import com.docpilot.backend.quality.vo.QualityEvalCaseResultDetail;
import com.docpilot.backend.quality.vo.QualityGateSummary;
import com.docpilot.backend.quality.vo.QualityRunDetail;
import com.docpilot.backend.quality.vo.QualityRunDiagnostics;
import com.docpilot.backend.quality.vo.QualityRunSummary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class QualityDomainTrendAssembler {

    private static final String MEMORY_QUALITY_DOMAIN = "memoryQuality";
    private static final String RAG_REPRESENTATIVE_DOMAIN = "ragRepresentativeEval";
    private static final List<String> MEMORY_QUALITY_METRICS = List.of(
            "casePassRate",
            "extractedSuggestionCount",
            "memoryCount",
            "evidenceCount",
            "memoryTriggerCount",
            "memoryHitCount",
            "memoryReviewCount",
            "ragEvidenceCount"
    );
    private static final List<String> MEMORY_QUALITY_FLAGS = List.of(
            "conflictAcceptBlocked",
            "conflictWithIdPresent",
            "sensitiveEditBlocked",
            "replaceResolvedActive",
            "mergeResolvedActive"
    );
    private static final List<String> RAG_REPRESENTATIVE_METRICS = List.of(
            "casePassRate",
            "caseCount",
            "targetCaseCount",
            "targetCoveragePassCount",
            "noEvidenceCaseCount",
            "noEvidenceCorrectCount",
            "targetQualityCaseCount",
            "strictImprovementCaseCount",
            "upliftCaseCount",
            "targetCoverageRegressionCount",
            "citationLeakageCount",
            "noEvidenceRegressionCount",
            "targetRerankAppliedCaseCount"
    );
    private static final List<String> RAG_REPRESENTATIVE_FLAGS = List.of(
            "rerankApplied",
            "multiQueryApplied"
    );

    Map<String, QualityDomainTrendSummary> build(List<QualityRunDetail> details) {
        Map<String, QualityDomainTrendSummary> trends = new LinkedHashMap<>();
        QualityDomainTrendSummary memoryTrend = buildDomainTrend(
                MEMORY_QUALITY_DOMAIN,
                "Memory quality smoke",
                details.stream().filter(this::isMemoryQualityRun).toList(),
                MEMORY_QUALITY_METRICS,
                MEMORY_QUALITY_FLAGS
        );
        if (memoryTrend.runCount() > 0) {
            trends.put(MEMORY_QUALITY_DOMAIN, memoryTrend);
        }
        QualityDomainTrendSummary ragRepresentativeTrend = buildDomainTrend(
                RAG_REPRESENTATIVE_DOMAIN,
                "RAG representative eval",
                details.stream().filter(this::isRagRepresentativeRun).toList(),
                RAG_REPRESENTATIVE_METRICS,
                RAG_REPRESENTATIVE_FLAGS
        );
        if (ragRepresentativeTrend.runCount() > 0) {
            trends.put(RAG_REPRESENTATIVE_DOMAIN, ragRepresentativeTrend);
        }
        return trends;
    }

    private QualityDomainTrendSummary buildDomainTrend(String domain,
                                                       String label,
                                                       List<QualityRunDetail> details,
                                                       List<String> metricNames,
                                                       List<String> flagNames) {
        List<QualityDomainTrendPoint> points = new ArrayList<>();
        Map<String, Integer> failureBucketCounts = new LinkedHashMap<>();
        Map<String, Integer> reviewBucketCounts = new LinkedHashMap<>();
        MetricAverageAccumulator metricAccumulator = new MetricAverageAccumulator();
        double passRateSum = 0.0;
        int passRateCount = 0;

        for (QualityRunDetail detail : details) {
            Map<String, Number> metrics = domainMetrics(detail, metricNames);
            Map<String, Boolean> flags = domainFlags(detail, flagNames);
            Double passRate = domainPassRate(detail, metrics);
            if (passRate != null) {
                passRateSum += passRate;
                passRateCount++;
            }
            metricAccumulator.accept(metrics);
            List<String> failureBuckets = domainBuckets(detail, "failure");
            List<String> reviewBuckets = domainBuckets(detail, "review");
            failureBuckets.forEach(bucket -> increment(failureBucketCounts, bucket));
            reviewBuckets.forEach(bucket -> increment(reviewBucketCounts, bucket));
            QualityRunSummary summary = detail.summary();
            points.add(new QualityDomainTrendPoint(
                    summary.marker(),
                    summary.status(),
                    summary.updatedAt(),
                    passRate,
                    metrics,
                    flags,
                    failureBuckets,
                    reviewBuckets
            ));
        }

        QualityDomainTrendPoint latest = points.isEmpty() ? null : points.get(0);
        return new QualityDomainTrendSummary(
                domain,
                label,
                points.size(),
                latest == null ? "" : latest.marker(),
                latest == null ? "REVIEW" : latest.status(),
                latest == null ? null : latest.updatedAt(),
                average(passRateSum, passRateCount),
                latest == null ? Map.of() : latest.metrics(),
                latest == null ? Map.of() : latest.flags(),
                metricAccumulator.averageMetrics(),
                failureBucketCounts,
                reviewBucketCounts,
                points
        );
    }

    private boolean isMemoryQualityRun(QualityRunDetail detail) {
        String source = detail.summary().source();
        String marker = detail.summary().marker();
        return "backend/target/memory-quality".equals(source)
                || normalize(marker).contains("memoryquality");
    }

    private boolean isRagRepresentativeRun(QualityRunDetail detail) {
        String source = detail.summary().source();
        String marker = normalize(detail.summary().marker());
        return "backend/target/rag-quality".equals(source)
                && (marker.contains("rerankrepresentative")
                || hasGate(detail, "rerankRepresentativeEval")
                || hasGate(detail, RAG_REPRESENTATIVE_DOMAIN));
    }

    private boolean hasGate(QualityRunDetail detail, String gateName) {
        String expected = normalize(gateName);
        return detail.gates().stream()
                .anyMatch(gate -> expected.equals(normalize(gate.name())));
    }

    private Map<String, Number> domainMetrics(QualityRunDetail detail, List<String> metricNames) {
        LinkedHashMap<String, Number> metrics = new LinkedHashMap<>();
        for (String metricName : metricNames) {
            Double value = findMetric(detail, metricName);
            if (value != null) {
                metrics.put(metricName, value);
            }
        }
        if (isMemoryQualityRun(detail)) {
            QualityRunDiagnostics.MemoryQualitySummary memory = detail.diagnostics().memoryQuality();
            putIfPresent(metrics, "memoryTriggerCount", memory.memoryTriggerCount());
            putIfPresent(metrics, "memoryHitCount", memory.memoryHitCount());
            putIfPresent(metrics, "memoryReviewCount", memory.memoryReviewCount());
            putIfPresent(metrics, "ragEvidenceCount", memory.ragEvidenceCount());
        }
        return metrics;
    }

    private void putIfPresent(Map<String, Number> metrics, String name, Number value) {
        if (value != null) {
            metrics.putIfAbsent(name, value);
        }
    }

    private Map<String, Boolean> domainFlags(QualityRunDetail detail, List<String> flagNames) {
        LinkedHashMap<String, Boolean> flags = new LinkedHashMap<>();
        for (String flagName : flagNames) {
            Boolean value = findFlag(detail, flagName);
            if (value != null) {
                flags.put(flagName, value);
            }
        }
        return flags;
    }

    private Boolean findFlag(QualityRunDetail detail, String name) {
        String expected = normalize(name);
        Boolean found = null;
        for (QualityGateSummary gate : detail.gates()) {
            for (Map.Entry<String, Boolean> entry : gate.flags().entrySet()) {
                if (expected.equals(normalize(entry.getKey()))) {
                    found = Boolean.TRUE.equals(found) || Boolean.TRUE.equals(entry.getValue());
                }
            }
        }
        for (QualityEvalCaseResultDetail item : detail.evalCases()) {
            for (Map.Entry<String, Boolean> entry : item.flags().entrySet()) {
                if (expected.equals(normalize(entry.getKey()))) {
                    found = Boolean.TRUE.equals(found) || Boolean.TRUE.equals(entry.getValue());
                }
            }
        }
        return found;
    }

    private Double domainPassRate(QualityRunDetail detail, Map<String, Number> metrics) {
        Number explicit = metrics.get("casePassRate");
        if (explicit != null) {
            return explicit.doubleValue();
        }
        Number caseCount = metrics.get("caseCount");
        Number targetCoverage = metrics.get("targetCoveragePassCount");
        Number noEvidenceCorrect = metrics.get("noEvidenceCorrectCount");
        if (caseCount != null && caseCount.doubleValue() > 0
                && (targetCoverage != null || noEvidenceCorrect != null)) {
            double numerator = (targetCoverage == null ? 0.0 : targetCoverage.doubleValue())
                    + (noEvidenceCorrect == null ? 0.0 : noEvidenceCorrect.doubleValue());
            return numerator / caseCount.doubleValue();
        }
        String status = normalizeStatus(detail.summary().status());
        if ("PASS".equals(status) || "SUCCESS".equals(status) || "OK".equals(status)) {
            return 1.0;
        }
        if (status.startsWith("FAILED") || "FAIL".equals(status) || "ERROR".equals(status)) {
            return 0.0;
        }
        return null;
    }

    private List<String> domainBuckets(QualityRunDetail detail, String type) {
        LinkedHashSet<String> buckets = new LinkedHashSet<>();
        if ("failure".equals(type)) {
            buckets.addAll(detail.summary().failureBuckets());
        } else {
            buckets.addAll(detail.summary().reviewBuckets());
        }
        for (QualityGateSummary gate : detail.gates()) {
            if (isDomainGate(detail, gate)) {
                buckets.addAll("failure".equals(type) ? gate.failureBuckets() : gate.reviewBuckets());
            }
        }
        for (QualityEvalCaseResultDetail item : detail.evalCases()) {
            if ("failure".equals(type)) {
                buckets.addAll(item.failureBuckets());
            } else {
                buckets.addAll(item.reviewBuckets());
            }
        }
        return List.copyOf(buckets);
    }

    private boolean isDomainGate(QualityRunDetail detail, QualityGateSummary gate) {
        String normalized = normalize(gate.name());
        if (isMemoryQualityRun(detail)) {
            return normalized.contains("memoryquality");
        }
        if (isRagRepresentativeRun(detail)) {
            return normalized.contains("rerankrepresentativeeval")
                    || normalized.contains(normalize(RAG_REPRESENTATIVE_DOMAIN));
        }
        return false;
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

    private void increment(Map<String, Integer> counts, String key) {
        String resolved = key == null || key.isBlank() ? "UNKNOWN" : key;
        counts.merge(resolved, 1, Integer::sum);
    }

    private Double average(double sum, int count) {
        return count <= 0 ? null : sum / count;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "REVIEW";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static final class MetricAverageAccumulator {
        private final Map<String, Double> sums = new LinkedHashMap<>();
        private final Map<String, Integer> counts = new LinkedHashMap<>();

        private void accept(Map<String, Number> metrics) {
            if (metrics == null) {
                return;
            }
            metrics.forEach((name, value) -> {
                if (value == null) {
                    return;
                }
                sums.merge(name, value.doubleValue(), Double::sum);
                counts.merge(name, 1, Integer::sum);
            });
        }

        private Map<String, Number> averageMetrics() {
            LinkedHashMap<String, Number> averages = new LinkedHashMap<>();
            sums.forEach((name, sum) -> {
                Integer count = counts.get(name);
                if (count != null && count > 0) {
                    averages.put(name, sum / count);
                }
            });
            return averages;
        }
    }
}
