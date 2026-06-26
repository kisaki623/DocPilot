package com.docpilot.backend.ai.context.builder;

import com.docpilot.backend.ai.context.ContextItem;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;

import java.util.List;
import java.util.Map;

public record KnowledgeBaseEvidenceResult(
        boolean triggered,
        boolean required,
        boolean noEvidence,
        String fallbackAnswer,
        List<ContextItem> items,
        List<KnowledgeBaseRagEvidenceCitation> citations,
        Map<Long, Integer> documentHitCounts
) {

    public KnowledgeBaseEvidenceResult {
        fallbackAnswer = fallbackAnswer == null ? "" : fallbackAnswer.trim();
        items = items == null ? List.of() : List.copyOf(items);
        citations = citations == null ? List.of() : List.copyOf(citations);
        documentHitCounts = documentHitCounts == null ? Map.of() : Map.copyOf(documentHitCounts);
    }

    public static KnowledgeBaseEvidenceResult notTriggered() {
        return new KnowledgeBaseEvidenceResult(false, false, false, "", List.of(), List.of(), Map.of());
    }
}
