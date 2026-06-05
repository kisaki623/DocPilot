package com.docpilot.backend.ai.rag.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record KnowledgeBaseRagEvalMetrics(
        int caseCount,
        double hitAtK,
        double documentHitRate,
        double citationHitRate,
        double noEvidenceRate,
        double scopeViolationRate
) {

    public static KnowledgeBaseRagEvalMetrics from(List<KnowledgeBaseRagEvalResult.CaseEvaluation> evaluations) {
        List<KnowledgeBaseRagEvalResult.CaseEvaluation> resolved =
                evaluations == null ? List.of() : List.copyOf(evaluations);
        int positiveCases = (int) resolved.stream().filter(item -> !item.expectedNoEvidence()).count();
        int noEvidenceCases = (int) resolved.stream().filter(KnowledgeBaseRagEvalResult.CaseEvaluation::expectedNoEvidence).count();
        int hitCount = (int) resolved.stream().filter(item -> !item.expectedNoEvidence() && item.hit()).count();
        int documentHitCount = (int) resolved.stream().filter(item -> !item.expectedNoEvidence() && item.documentHit()).count();
        int citationHitCount = (int) resolved.stream().filter(item -> !item.expectedNoEvidence() && item.citationHit()).count();
        int noEvidenceHitCount = (int) resolved.stream().filter(item -> item.expectedNoEvidence() && item.noEvidenceHit()).count();
        int scopeViolationCount = (int) resolved.stream().filter(KnowledgeBaseRagEvalResult.CaseEvaluation::scopeViolation).count();
        return new KnowledgeBaseRagEvalMetrics(
                resolved.size(),
                rate(hitCount, positiveCases),
                rate(documentHitCount, positiveCases),
                rate(citationHitCount, positiveCases),
                rate(noEvidenceHitCount, noEvidenceCases),
                resolved.isEmpty() ? 0.0D : (double) scopeViolationCount / resolved.size()
        );
    }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("caseCount", caseCount);
        value.put("hitAtK", format(hitAtK));
        value.put("documentHitRate", format(documentHitRate));
        value.put("citationHitRate", format(citationHitRate));
        value.put("noEvidenceRate", format(noEvidenceRate));
        value.put("scopeViolationRate", format(scopeViolationRate));
        return value;
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static double rate(int numerator, int denominator) {
        return denominator == 0 ? 1.0D : (double) numerator / denominator;
    }
}
