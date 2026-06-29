package com.docpilot.backend.ai.rag.eval;

import java.util.List;

final class RagClaimSupportScorer {

    private RagClaimSupportScorer() {
    }

    static RagClaimSupportScore score(RagRealQaEvalCase evalCase,
                                      KnowledgeBaseRagEvalResult.CaseEvaluation evaluation) {
        List<RagRealQaEvalCase.ExpectedClaim> claims = evalCase.expectedClaims();
        if (claims.isEmpty()) {
            return new RagClaimSupportScore(false, 0, 0, 0, true, evaluation.forbiddenAnswerHit());
        }
        boolean forbiddenClaimHit = evaluation.forbiddenAnswerHit();
        int supported = 0;
        for (RagRealQaEvalCase.ExpectedClaim claim : claims) {
            if (isSupported(evalCase, evaluation, claim, forbiddenClaimHit)) {
                supported++;
            }
        }
        int unsupported = claims.size() - supported;
        return new RagClaimSupportScore(
                true,
                claims.size(),
                supported,
                unsupported,
                unsupported == 0 && !forbiddenClaimHit,
                forbiddenClaimHit
        );
    }

    private static boolean isSupported(RagRealQaEvalCase evalCase,
                                       KnowledgeBaseRagEvalResult.CaseEvaluation evaluation,
                                       RagRealQaEvalCase.ExpectedClaim claim,
                                       boolean forbiddenClaimHit) {
        return !evalCase.expectedNoEvidence()
                && !forbiddenClaimHit
                && !evaluation.scopeViolation()
                && evaluation.answerHit()
                && evaluation.citationHit()
                && evaluation.groundedAnswerHit()
                && containsAll(evalCase.expectedAnswerMarkers(), claim.answerMarkers())
                && containsAll(evalCase.requiredCitationMarkers(), claim.evidenceMarkers());
    }

    private static boolean containsAll(List<String> haystack, List<String> needles) {
        return haystack != null && needles != null && haystack.containsAll(needles);
    }
}
