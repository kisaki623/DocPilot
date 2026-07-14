package com.docpilot.backend.ai.context.builder;

import com.docpilot.backend.ai.context.ContextItem;
import com.docpilot.backend.ai.context.ContextTraceTechnicalDetails;
import com.docpilot.backend.ai.context.RouteDecision;
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
        Map<Long, Integer> documentHitCounts,
        RouteDecision routeDecision,
        ContextTraceTechnicalDetails.RetrievalDetails retrievalDetails
) {

    public KnowledgeBaseEvidenceResult(boolean triggered,
                                       boolean required,
                                       boolean noEvidence,
                                       String fallbackAnswer,
                                       List<ContextItem> items,
                                       List<KnowledgeBaseRagEvidenceCitation> citations,
                                       Map<Long, Integer> documentHitCounts) {
        this(triggered, required, noEvidence, fallbackAnswer, items, citations, documentHitCounts,
                triggered ? RouteDecision.AUTO_RAG_EVIDENCE : RouteDecision.MODEL_ONLY,
                triggered
                        ? ContextTraceTechnicalDetails.RetrievalDetails.notRun("LEGACY_RETRIEVAL_DETAILS_UNAVAILABLE")
                        : ContextTraceTechnicalDetails.RetrievalDetails.notRun("NOT_TRIGGERED"));
    }

    public KnowledgeBaseEvidenceResult(boolean triggered,
                                       boolean required,
                                       boolean noEvidence,
                                       String fallbackAnswer,
                                       List<ContextItem> items,
                                       List<KnowledgeBaseRagEvidenceCitation> citations,
                                       Map<Long, Integer> documentHitCounts,
                                       RouteDecision routeDecision) {
        this(triggered, required, noEvidence, fallbackAnswer, items, citations, documentHitCounts,
                routeDecision,
                triggered
                        ? ContextTraceTechnicalDetails.RetrievalDetails.notRun("LEGACY_RETRIEVAL_DETAILS_UNAVAILABLE")
                        : ContextTraceTechnicalDetails.RetrievalDetails.notRun("NOT_TRIGGERED"));
    }

    public KnowledgeBaseEvidenceResult {
        fallbackAnswer = fallbackAnswer == null ? "" : fallbackAnswer.trim();
        items = items == null ? List.of() : List.copyOf(items);
        citations = citations == null ? List.of() : List.copyOf(citations);
        documentHitCounts = documentHitCounts == null ? Map.of() : Map.copyOf(documentHitCounts);
        routeDecision = routeDecision == null ? RouteDecision.LEGACY_UNKNOWN : routeDecision;
        retrievalDetails = retrievalDetails == null
                ? ContextTraceTechnicalDetails.RetrievalDetails.notRun("NOT_TRIGGERED")
                : retrievalDetails;
    }

    public static KnowledgeBaseEvidenceResult notTriggered() {
        return notTriggered(RouteDecision.MODEL_ONLY);
    }

    public static KnowledgeBaseEvidenceResult notTriggered(RouteDecision routeDecision) {
        return new KnowledgeBaseEvidenceResult(false, false, false, "", List.of(), List.of(), Map.of(), routeDecision,
                ContextTraceTechnicalDetails.RetrievalDetails.notRun(routeDecision == null ? "NOT_TRIGGERED" : routeDecision.name()));
    }
}
