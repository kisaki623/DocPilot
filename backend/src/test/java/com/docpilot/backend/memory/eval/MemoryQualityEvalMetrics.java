package com.docpilot.backend.memory.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record MemoryQualityEvalMetrics(
        int caseCount,
        double casePassRate,
        double suggestionTypeRecallRate,
        double activeMemoryPrecisionRate,
        double sensitiveRejectionRate,
        double suggestionSafetyRate,
        double ragEvidenceIsolationRate,
        double userSignalExtractionRate,
        double noiseSuppressionRate,
        double temporaryInstructionSuppressionRate,
        double traceSourceCountRate
) {

    public static MemoryQualityEvalMetrics from(List<MemoryQualityEvalResult.CaseEvaluation> evaluations) {
        List<MemoryQualityEvalResult.CaseEvaluation> resolved = evaluations == null ? List.of() : List.copyOf(evaluations);
        int passed = (int) resolved.stream().filter(MemoryQualityEvalResult.CaseEvaluation::passed).count();
        int suggestionHit = (int) resolved.stream().filter(MemoryQualityEvalResult.CaseEvaluation::suggestionTypesHit).count();
        int activeHit = (int) resolved.stream().filter(MemoryQualityEvalResult.CaseEvaluation::activeMemorySelectionHit).count();
        int sensitiveRequired = (int) resolved.stream().filter(MemoryQualityEvalResult.CaseEvaluation::sensitiveRejectionExpected).count();
        int sensitiveHit = (int) resolved.stream().filter(item -> item.sensitiveRejectionExpected() && item.sensitiveRejected()).count();
        int suggestionSafetyHit = (int) resolved.stream().filter(MemoryQualityEvalResult.CaseEvaluation::suggestionSafetyHit).count();
        int isolationHit = (int) resolved.stream().filter(MemoryQualityEvalResult.CaseEvaluation::ragEvidenceIsolationHit).count();
        int traceHit = (int) resolved.stream().filter(MemoryQualityEvalResult.CaseEvaluation::traceCountsHit).count();
        List<MemoryQualityEvalResult.CaseEvaluation> userSignalCases = byCategory(resolved,
                "preference_extraction", "multi_signal_extraction");
        List<MemoryQualityEvalResult.CaseEvaluation> noiseCases = byCategory(resolved,
                "rag_evidence_isolation",
                "assistant_contamination",
                "low_value_suppression",
                "temporary_instruction_suppression",
                "sensitive_suggestion_suppression");
        List<MemoryQualityEvalResult.CaseEvaluation> temporaryCases = byCategory(resolved,
                "temporary_instruction_suppression");
        return new MemoryQualityEvalMetrics(
                resolved.size(),
                rate(passed, resolved.size()),
                rate(suggestionHit, resolved.size()),
                rate(activeHit, resolved.size()),
                rate(sensitiveHit, sensitiveRequired),
                rate(suggestionSafetyHit, resolved.size()),
                rate(isolationHit, resolved.size()),
                passRate(userSignalCases),
                passRate(noiseCases),
                passRate(temporaryCases),
                rate(traceHit, resolved.size())
        );
    }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("caseCount", caseCount);
        value.put("casePassRate", format(casePassRate));
        value.put("suggestionTypeRecallRate", format(suggestionTypeRecallRate));
        value.put("activeMemoryPrecisionRate", format(activeMemoryPrecisionRate));
        value.put("sensitiveRejectionRate", format(sensitiveRejectionRate));
        value.put("suggestionSafetyRate", format(suggestionSafetyRate));
        value.put("ragEvidenceIsolationRate", format(ragEvidenceIsolationRate));
        value.put("userSignalExtractionRate", format(userSignalExtractionRate));
        value.put("noiseSuppressionRate", format(noiseSuppressionRate));
        value.put("temporaryInstructionSuppressionRate", format(temporaryInstructionSuppressionRate));
        value.put("traceSourceCountRate", format(traceSourceCountRate));
        return value;
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static double rate(int numerator, int denominator) {
        return denominator == 0 ? 1.0D : (double) numerator / denominator;
    }

    private static double passRate(List<MemoryQualityEvalResult.CaseEvaluation> evaluations) {
        int passed = (int) evaluations.stream().filter(MemoryQualityEvalResult.CaseEvaluation::passed).count();
        return rate(passed, evaluations.size());
    }

    private static List<MemoryQualityEvalResult.CaseEvaluation> byCategory(
            List<MemoryQualityEvalResult.CaseEvaluation> evaluations,
            String... categories
    ) {
        List<String> wanted = List.of(categories);
        return evaluations.stream()
                .filter(evaluation -> wanted.contains(evaluation.category()))
                .toList();
    }
}
