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
        double answerHitRate,
        double citationCountRate,
        double multiDocumentCoverageRate,
        double groundedAnswerRate,
        double forbiddenAnswerLeakRate,
        double noEvidenceRate,
        double noEvidenceCitationFreeRate,
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
        int answerHitCount = (int) resolved.stream().filter(item -> !item.expectedNoEvidence() && item.answerHit()).count();
        int citationCountHitCount = (int) resolved.stream().filter(item -> !item.expectedNoEvidence() && item.citationCountHit()).count();
        List<KnowledgeBaseRagEvalResult.CaseEvaluation> multiDocumentCases = resolved.stream()
                .filter(KnowledgeBaseRagEvalResult.CaseEvaluation::multiDocumentCoverageRequired)
                .toList();
        int multiDocumentCoverageHitCount = (int) multiDocumentCases.stream()
                .filter(KnowledgeBaseRagEvalResult.CaseEvaluation::multiDocumentCoverageHit)
                .count();
        int forbiddenAnswerLeakCount = (int) resolved.stream()
                .filter(KnowledgeBaseRagEvalResult.CaseEvaluation::forbiddenAnswerHit)
                .count();
        int noEvidenceHitCount = (int) resolved.stream().filter(item -> item.expectedNoEvidence() && item.noEvidenceHit()).count();
        int groundedAnswerHitCount = (int) resolved.stream()
                .filter(item -> !item.expectedNoEvidence() && item.groundedAnswerHit())
                .count();
        int noEvidenceCitationFreeHitCount = (int) resolved.stream()
                .filter(item -> item.expectedNoEvidence() && item.noEvidenceCitationFreeHit())
                .count();
        int scopeViolationCount = (int) resolved.stream().filter(KnowledgeBaseRagEvalResult.CaseEvaluation::scopeViolation).count();
        return new KnowledgeBaseRagEvalMetrics(
                resolved.size(),
                rate(hitCount, positiveCases),
                rate(documentHitCount, positiveCases),
                rate(citationHitCount, positiveCases),
                rate(answerHitCount, positiveCases),
                rate(citationCountHitCount, positiveCases),
                rate(multiDocumentCoverageHitCount, multiDocumentCases.size()),
                rate(groundedAnswerHitCount, positiveCases),
                resolved.isEmpty() ? 0.0D : (double) forbiddenAnswerLeakCount / resolved.size(),
                rate(noEvidenceHitCount, noEvidenceCases),
                rate(noEvidenceCitationFreeHitCount, noEvidenceCases),
                resolved.isEmpty() ? 0.0D : (double) scopeViolationCount / resolved.size()
        );
    }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("caseCount", caseCount);
        value.put("hitAtK", format(hitAtK));
        value.put("documentHitRate", format(documentHitRate));
        value.put("citationHitRate", format(citationHitRate));
        value.put("answerHitRate", format(answerHitRate));
        value.put("citationCountRate", format(citationCountRate));
        value.put("multiDocumentCoverageRate", format(multiDocumentCoverageRate));
        value.put("groundedAnswerRate", format(groundedAnswerRate));
        value.put("forbiddenAnswerLeakRate", format(forbiddenAnswerLeakRate));
        value.put("noEvidenceRate", format(noEvidenceRate));
        value.put("noEvidenceCitationFreeRate", format(noEvidenceCitationFreeRate));
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
