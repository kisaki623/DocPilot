package com.docpilot.backend.ai.rag;

import java.util.DoubleSummaryStatistics;
import java.util.List;

public record KnowledgeBaseRagScoreSummary(
        int count,
        Double min,
        Double max
) {

    public KnowledgeBaseRagScoreSummary {
        count = Math.max(0, count);
        min = finiteOrNull(min);
        max = finiteOrNull(max);
    }

    public static KnowledgeBaseRagScoreSummary empty() {
        return new KnowledgeBaseRagScoreSummary(0, null, null);
    }

    public static KnowledgeBaseRagScoreSummary fromScores(List<Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return empty();
        }
        DoubleSummaryStatistics statistics = scores.stream()
                .filter(value -> value != null && Double.isFinite(value))
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();
        if (statistics.getCount() == 0) {
            return empty();
        }
        return new KnowledgeBaseRagScoreSummary(
                Math.toIntExact(statistics.getCount()),
                statistics.getMin(),
                statistics.getMax()
        );
    }

    private static Double finiteOrNull(Double value) {
        return value != null && Double.isFinite(value) ? value : null;
    }
}
