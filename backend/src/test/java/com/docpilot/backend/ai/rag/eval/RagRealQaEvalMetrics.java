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
        double rerankUpliftCandidateRate,
        double rerankUpliftCandidatePassRate,
        double longDocumentCasePassRate,
        double nearMissNoEvidenceRate,
        double multiDocSummaryPassRate,
        double distractorSuppressionRate,
        double hardNegativePassRate,
        double answerFaithfulnessPassRate,
        double claimSupportPassRate,
        double numericFaithfulnessPassRate
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
        int rerankCandidatePassed = (int) resolved.stream()
                .filter(RagRealQaEvalResult.CaseEvaluation::rerankUpliftCandidate)
                .filter(RagRealQaEvalResult.CaseEvaluation::passed)
                .count();
        List<RagRealQaEvalResult.CaseEvaluation> longDocumentCases = byCategory(resolved, "long_document");
        List<RagRealQaEvalResult.CaseEvaluation> nearMissNoEvidenceCases = byCategory(resolved, "near_miss_no_evidence");
        List<RagRealQaEvalResult.CaseEvaluation> multiDocSummaryCases = resolved.stream()
                .filter(item -> "multi_doc_summary".equals(item.category())
                        || "cross_document_summary".equals(item.category()))
                .toList();
        List<RagRealQaEvalResult.CaseEvaluation> distractorCases = resolved.stream()
                .filter(RagRealQaEvalMetrics::isDistractorCase)
                .toList();
        List<RagRealQaEvalResult.CaseEvaluation> hardNegativeCases = byCategory(resolved, "hard_negative");
        List<RagRealQaEvalResult.CaseEvaluation> answerFaithfulnessCases = byCategory(resolved, "answer_faithfulness");
        List<RagRealQaEvalResult.CaseEvaluation> claimSupportCases = byCategory(resolved, "claim_support");
        List<RagRealQaEvalResult.CaseEvaluation> numericFaithfulnessCases = byCategory(resolved, "numeric_faithfulness");
        return new RagRealQaEvalMetrics(
                resolved.size(),
                rate(passed, resolved.size()),
                rate(answerCorrect, positiveCases),
                rate(citationGrounded, positiveCases),
                rate(noEvidenceHit, noEvidenceCases),
                rate(multiDocumentHit, multiDocumentRequired),
                resolved.isEmpty() ? 0.0D : (double) forbiddenLeak / resolved.size(),
                resolved.isEmpty() ? 0.0D : (double) scopeViolation / resolved.size(),
                resolved.isEmpty() ? 0.0D : (double) rerankCandidates / resolved.size(),
                rate(rerankCandidatePassed, rerankCandidates),
                passRate(longDocumentCases),
                rate((int) nearMissNoEvidenceCases.stream()
                        .filter(RagRealQaEvalResult.CaseEvaluation::noEvidenceHit)
                        .count(), nearMissNoEvidenceCases.size()),
                passRate(multiDocSummaryCases),
                passRate(distractorCases),
                passRate(hardNegativeCases),
                passRate(answerFaithfulnessCases),
                passRate(claimSupportCases),
                passRate(numericFaithfulnessCases)
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
        value.put("rerankUpliftCandidatePassRate", format(rerankUpliftCandidatePassRate));
        value.put("longDocumentCasePassRate", format(longDocumentCasePassRate));
        value.put("nearMissNoEvidenceRate", format(nearMissNoEvidenceRate));
        value.put("multiDocSummaryPassRate", format(multiDocSummaryPassRate));
        value.put("distractorSuppressionRate", format(distractorSuppressionRate));
        value.put("hardNegativePassRate", format(hardNegativePassRate));
        value.put("answerFaithfulnessPassRate", format(answerFaithfulnessPassRate));
        value.put("claimSupportPassRate", format(claimSupportPassRate));
        value.put("numericFaithfulnessPassRate", format(numericFaithfulnessPassRate));
        return value;
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static double rate(int numerator, int denominator) {
        return denominator == 0 ? 1.0D : (double) numerator / denominator;
    }

    private static List<RagRealQaEvalResult.CaseEvaluation> byCategory(
            List<RagRealQaEvalResult.CaseEvaluation> evaluations,
            String category
    ) {
        return evaluations.stream()
                .filter(item -> category.equals(item.category()))
                .toList();
    }

    private static boolean isDistractorCase(RagRealQaEvalResult.CaseEvaluation evaluation) {
        String category = evaluation.category();
        return category.contains("distractor")
                || category.contains("noise")
                || category.contains("near_miss")
                || category.contains("semantic")
                || category.contains("rerank")
                || category.contains("hard_negative");
    }

    private static double passRate(List<RagRealQaEvalResult.CaseEvaluation> evaluations) {
        List<RagRealQaEvalResult.CaseEvaluation> resolved = evaluations == null ? List.of() : evaluations;
        int passed = (int) resolved.stream().filter(RagRealQaEvalResult.CaseEvaluation::passed).count();
        return rate(passed, resolved.size());
    }
}
