package com.docpilot.backend.ai.rag.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record RagRealQaEvalMetrics(
        int caseCount,
        double casePassRate,
        double answerCorrectnessRate,
        double citationGroundingRate,
        double noEvidencePrecision,
        double multiDocumentCoverageRate,
        double forbiddenLeakRate,
        double scopeViolationRate,
        double rerankUpliftCandidateRate
) {

    public static RagRealQaEvalMetrics from(List<RagRealQaEvalResult.CaseEvaluation> evaluations) {
        List<RagRealQaEvalResult.CaseEvaluation> resolved = evaluations == null ? List.of() : List.copyOf(evaluations);
        int positiveCases = (int) resolved.stream().filter(item -> !item.expectedNoEvidence()).count();
        int noEvidenceCases = (int) resolved.stream().filter(RagRealQaEvalResult.CaseEvaluation::expectedNoEvidence).count();
        int passed = (int) resolved.stream().filter(RagRealQaEvalResult.CaseEvaluation::passed).count();
        int answerCorrect = (int) resolved.stream().filter(item -> !item.expectedNoEvidence() && item.answerHit()).count();
        int citationGrounded = (int) resolved.stream().filter(item -> !item.expectedNoEvidence() && item.citationGrounded()).count();
        int noEvidenceHit = (int) resolved.stream().filter(item -> item.expectedNoEvidence() && item.noEvidenceHit()).count();
        int multiDocumentHit = (int) resolved.stream()
                .filter(item -> item.minDocumentCoverage() > 1 && item.multiDocumentCoverageHit())
                .count();
        int multiDocumentRequired = (int) resolved.stream()
                .filter(item -> item.minDocumentCoverage() > 1)
                .count();
        int forbiddenLeak = (int) resolved.stream().filter(RagRealQaEvalResult.CaseEvaluation::forbiddenAnswerHit).count();
        int scopeViolation = (int) resolved.stream().filter(RagRealQaEvalResult.CaseEvaluation::scopeViolation).count();
        int rerankCandidates = (int) resolved.stream().filter(RagRealQaEvalResult.CaseEvaluation::rerankUpliftCandidate).count();
        return new RagRealQaEvalMetrics(
                resolved.size(),
                rate(passed, resolved.size()),
                rate(answerCorrect, positiveCases),
                rate(citationGrounded, positiveCases),
                rate(noEvidenceHit, noEvidenceCases),
                rate(multiDocumentHit, multiDocumentRequired),
                resolved.isEmpty() ? 0.0D : (double) forbiddenLeak / resolved.size(),
                resolved.isEmpty() ? 0.0D : (double) scopeViolation / resolved.size(),
                resolved.isEmpty() ? 0.0D : (double) rerankCandidates / resolved.size()
        );
    }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("caseCount", caseCount);
        value.put("casePassRate", format(casePassRate));
        value.put("answerCorrectnessRate", format(answerCorrectnessRate));
        value.put("citationGroundingRate", format(citationGroundingRate));
        value.put("noEvidencePrecision", format(noEvidencePrecision));
        value.put("multiDocumentCoverageRate", format(multiDocumentCoverageRate));
        value.put("forbiddenLeakRate", format(forbiddenLeakRate));
        value.put("scopeViolationRate", format(scopeViolationRate));
        value.put("rerankUpliftCandidateRate", format(rerankUpliftCandidateRate));
        return value;
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static double rate(int numerator, int denominator) {
        return denominator == 0 ? 1.0D : (double) numerator / denominator;
    }
}
