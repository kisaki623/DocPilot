package com.docpilot.backend.ai.rag.eval;

public record RagClaimSupportScore(
        boolean required,
        int claimCount,
        int supportedClaimCount,
        int unsupportedClaimCount,
        boolean claimSupportHit,
        boolean forbiddenClaimHit
) {

    public RagClaimSupportScore {
        claimCount = Math.max(0, claimCount);
        supportedClaimCount = Math.max(0, supportedClaimCount);
        unsupportedClaimCount = Math.max(0, unsupportedClaimCount);
        if (supportedClaimCount + unsupportedClaimCount > claimCount) {
            unsupportedClaimCount = Math.max(0, claimCount - supportedClaimCount);
        }
        claimSupportHit = !required || claimSupportHit;
    }
}
