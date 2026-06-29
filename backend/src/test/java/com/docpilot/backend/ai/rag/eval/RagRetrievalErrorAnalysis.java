package com.docpilot.backend.ai.rag.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RagRetrievalErrorAnalysis(
        int caseCount,
        int failedCaseCount,
        int missedRetrievalCount,
        int wrongRetrievalCount,
        int noEvidenceRefusalPassCount,
        int noEvidenceRefusalMissCount,
        int citationUnsupportedCount,
        int answerUnsupportedCount,
        int forbiddenLeakCount,
        int scopeViolationCount,
        int rankingCandidateCount,
        int rankingCandidatePassCount,
        Map<String, Integer> failureReasonCounts
) {

    public RagRetrievalErrorAnalysis {
        failureReasonCounts = failureReasonCounts == null ? Map.of() : Map.copyOf(failureReasonCounts);
    }

    public static RagRetrievalErrorAnalysis fromKnowledgeBase(List<KnowledgeBaseRagEvalResult.CaseEvaluation> evaluations) {
        List<KnowledgeBaseRagEvalResult.CaseEvaluation> resolved = evaluations == null ? List.of() : List.copyOf(evaluations);
        int noEvidencePass = 0;
        int noEvidenceMiss = 0;
        int missedRetrieval = 0;
        int wrongRetrieval = 0;
        int citationUnsupported = 0;
        int answerUnsupported = 0;
        int forbiddenLeak = 0;
        int scopeViolation = 0;
        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (KnowledgeBaseRagEvalResult.CaseEvaluation evaluation : resolved) {
            countReasons(reasons, evaluation.failureReasons());
            if (evaluation.expectedNoEvidence()) {
                if (evaluation.noEvidenceHit() && evaluation.noEvidenceCitationFreeHit()) {
                    noEvidencePass++;
                } else {
                    noEvidenceMiss++;
                }
            }
            if (hasAny(evaluation.failureReasons(), "retrieval_marker_miss", "document_hit_miss")) {
                missedRetrieval++;
            }
            if (hasAny(evaluation.failureReasons(), "scope_violation")) {
                wrongRetrieval++;
            }
            if (hasAny(evaluation.failureReasons(), "citation_hit_miss", "citation_count_miss")) {
                citationUnsupported++;
            }
            if (hasAny(evaluation.failureReasons(), "grounded_answer_miss", "answer_marker_miss")) {
                answerUnsupported++;
            }
            if (evaluation.forbiddenAnswerHit()) {
                forbiddenLeak++;
            }
            if (evaluation.scopeViolation()) {
                scopeViolation++;
            }
        }
        return new RagRetrievalErrorAnalysis(
                resolved.size(),
                (int) resolved.stream().filter(item -> !item.passed()).count(),
                missedRetrieval,
                wrongRetrieval,
                noEvidencePass,
                noEvidenceMiss,
                citationUnsupported,
                answerUnsupported,
                forbiddenLeak,
                scopeViolation,
                0,
                0,
                reasons
        );
    }

    public static RagRetrievalErrorAnalysis fromRealQa(List<RagRealQaEvalResult.CaseEvaluation> evaluations) {
        List<RagRealQaEvalResult.CaseEvaluation> resolved = evaluations == null ? List.of() : List.copyOf(evaluations);
        int noEvidencePass = 0;
        int noEvidenceMiss = 0;
        int missedRetrieval = 0;
        int wrongRetrieval = 0;
        int citationUnsupported = 0;
        int answerUnsupported = 0;
        int forbiddenLeak = 0;
        int scopeViolation = 0;
        int rankingCandidates = 0;
        int rankingCandidatePass = 0;
        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (RagRealQaEvalResult.CaseEvaluation evaluation : resolved) {
            countReasons(reasons, evaluation.failureReasons());
            if (evaluation.expectedNoEvidence()) {
                if (evaluation.noEvidenceHit() && evaluation.citationCount() == 0) {
                    noEvidencePass++;
                } else {
                    noEvidenceMiss++;
                }
            }
            if (hasAny(evaluation.failureReasons(), "retrieval_marker_miss", "document_hit_miss", "document_coverage_miss")) {
                missedRetrieval++;
            }
            if (evaluation.scopeViolation()) {
                wrongRetrieval++;
            }
            if (hasAny(evaluation.failureReasons(), "citation_hit_miss", "citation_count_miss")) {
                citationUnsupported++;
            }
            if (hasAny(evaluation.failureReasons(), "grounded_answer_miss", "answer_marker_miss",
                    "claim_support_miss", "forbidden_claim_leak")) {
                answerUnsupported++;
            }
            if (evaluation.forbiddenAnswerHit() || evaluation.forbiddenClaimHit()) {
                forbiddenLeak++;
            }
            if (evaluation.scopeViolation()) {
                scopeViolation++;
            }
            if (evaluation.rerankUpliftCandidate()) {
                rankingCandidates++;
                if (evaluation.passed()) {
                    rankingCandidatePass++;
                }
            }
        }
        return new RagRetrievalErrorAnalysis(
                resolved.size(),
                (int) resolved.stream().filter(item -> !item.passed()).count(),
                missedRetrieval,
                wrongRetrieval,
                noEvidencePass,
                noEvidenceMiss,
                citationUnsupported,
                answerUnsupported,
                forbiddenLeak,
                scopeViolation,
                rankingCandidates,
                rankingCandidatePass,
                reasons
        );
    }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("caseCount", caseCount);
        value.put("failedCaseCount", failedCaseCount);
        value.put("missedRetrievalCount", missedRetrievalCount);
        value.put("wrongRetrievalCount", wrongRetrievalCount);
        value.put("noEvidenceRefusalPassCount", noEvidenceRefusalPassCount);
        value.put("noEvidenceRefusalMissCount", noEvidenceRefusalMissCount);
        value.put("citationUnsupportedCount", citationUnsupportedCount);
        value.put("answerUnsupportedCount", answerUnsupportedCount);
        value.put("forbiddenLeakCount", forbiddenLeakCount);
        value.put("scopeViolationCount", scopeViolationCount);
        value.put("rankingCandidateCount", rankingCandidateCount);
        value.put("rankingCandidatePassCount", rankingCandidatePassCount);
        value.put("failureReasonCounts", failureReasonCounts);
        value.put("notes", List.of(
                "Counts only; no query text, document text, model instructions, evidence context, or answer text is stored",
                "Ranking candidate counts are fixture-shaped signals, not a broad rerank benchmark"
        ));
        return value;
    }

    private static void countReasons(Map<String, Integer> counts, List<String> reasons) {
        if (reasons == null) {
            return;
        }
        for (String reason : reasons) {
            if (reason == null || reason.isBlank()) {
                continue;
            }
            counts.merge(reason.trim(), 1, Integer::sum);
        }
    }

    private static boolean hasAny(List<String> reasons, String... expected) {
        if (reasons == null || reasons.isEmpty()) {
            return false;
        }
        for (String reason : reasons) {
            for (String item : expected) {
                if (item.equals(reason)) {
                    return true;
                }
            }
        }
        return false;
    }
}
