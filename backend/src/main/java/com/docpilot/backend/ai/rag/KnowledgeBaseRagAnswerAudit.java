package com.docpilot.backend.ai.rag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record KnowledgeBaseRagAnswerAudit(
        boolean grounded,
        int evidenceCount,
        int citationCount,
        Map<Long, Integer> documentHitCounts,
        KnowledgeBaseRagScoreSummary scoreSummary,
        KnowledgeBaseRagScoreSummary vectorScoreSummary,
        KnowledgeBaseRagScoreSummary fusedScoreSummary,
        KnowledgeBaseRagScoreSummary rerankScoreSummary,
        String retrievalMode,
        boolean rerankApplied,
        String rerankModel,
        boolean fallbackUsed,
        String fallbackReason,
        int modelCallCount
) {

    public KnowledgeBaseRagAnswerAudit {
        evidenceCount = Math.max(0, evidenceCount);
        citationCount = Math.max(0, citationCount);
        documentHitCounts = documentHitCounts == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(documentHitCounts));
        scoreSummary = scoreSummary == null ? KnowledgeBaseRagScoreSummary.empty() : scoreSummary;
        vectorScoreSummary = vectorScoreSummary == null ? KnowledgeBaseRagScoreSummary.empty() : vectorScoreSummary;
        fusedScoreSummary = fusedScoreSummary == null ? KnowledgeBaseRagScoreSummary.empty() : fusedScoreSummary;
        rerankScoreSummary = rerankScoreSummary == null ? KnowledgeBaseRagScoreSummary.empty() : rerankScoreSummary;
        retrievalMode = retrievalMode == null ? "" : retrievalMode.trim();
        rerankModel = rerankModel == null ? "" : rerankModel.trim();
        fallbackReason = fallbackReason == null ? "" : fallbackReason.trim();
        modelCallCount = Math.max(0, modelCallCount);
    }

    public static KnowledgeBaseRagAnswerAudit from(KnowledgeBaseRagRetrievalResult retrieval,
                                                   boolean noEvidence,
                                                   boolean fallbackUsed,
                                                   String fallbackReason,
                                                   int modelCallCount) {
        if (retrieval == null) {
            return new KnowledgeBaseRagAnswerAudit(
                    false,
                    0,
                    0,
                    Map.of(),
                    KnowledgeBaseRagScoreSummary.empty(),
                    KnowledgeBaseRagScoreSummary.empty(),
                    KnowledgeBaseRagScoreSummary.empty(),
                    KnowledgeBaseRagScoreSummary.empty(),
                    "",
                    false,
                    "",
                    fallbackUsed,
                    fallbackReason,
                    modelCallCount
            );
        }
        List<KnowledgeBaseRagEvidenceCitation> citations = retrieval.citations();
        boolean grounded = !noEvidence && !fallbackUsed && !citations.isEmpty();
        return new KnowledgeBaseRagAnswerAudit(
                grounded,
                retrieval.hits().size(),
                citations.size(),
                retrieval.documentHitCounts(),
                KnowledgeBaseRagScoreSummary.fromScores(citations.stream().map(KnowledgeBaseRagEvidenceCitation::score).toList()),
                KnowledgeBaseRagScoreSummary.fromScores(citations.stream().map(KnowledgeBaseRagEvidenceCitation::vectorScore).toList()),
                KnowledgeBaseRagScoreSummary.fromScores(citations.stream().map(KnowledgeBaseRagEvidenceCitation::fusedScore).toList()),
                KnowledgeBaseRagScoreSummary.fromScores(citations.stream().map(KnowledgeBaseRagEvidenceCitation::rerankScore).toList()),
                retrieval.retrievalMode(),
                retrieval.rerankApplied(),
                retrieval.rerankModel(),
                fallbackUsed,
                fallbackReason,
                modelCallCount
        );
    }
}
