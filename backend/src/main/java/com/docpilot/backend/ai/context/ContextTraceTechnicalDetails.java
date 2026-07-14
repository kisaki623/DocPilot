package com.docpilot.backend.ai.context;

import com.docpilot.backend.ai.context.token.TokenBudgetResult;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ContextTraceTechnicalDetails(
        boolean available,
        String traceId,
        Long messageId,
        RouteDetails route,
        Map<String, Long> timingsMs,
        RetrievalDetails retrieval,
        TokenBudgetDetails tokenBudget,
        ContextUsageDetails contextUsage,
        FallbackDetails fallback
) {

    public ContextTraceTechnicalDetails {
        traceId = traceId == null ? "" : traceId.trim();
        route = route == null ? RouteDetails.empty() : route;
        timingsMs = timingsMs == null ? Map.of() : Map.copyOf(timingsMs);
        retrieval = retrieval == null ? RetrievalDetails.notRun("NOT_TRIGGERED") : retrieval;
        tokenBudget = tokenBudget == null ? TokenBudgetDetails.empty() : tokenBudget;
        contextUsage = contextUsage == null ? ContextUsageDetails.empty() : contextUsage;
        fallback = fallback == null ? FallbackDetails.empty() : fallback;
    }

    public static ContextTraceTechnicalDetails unavailable(Long conversationId, Long messageId) {
        return new ContextTraceTechnicalDetails(
                false,
                traceId(conversationId, messageId),
                messageId,
                RouteDetails.empty(),
                Map.of(),
                RetrievalDetails.notRun("LEGACY_TRACE_WITHOUT_TECHNICAL_DETAILS"),
                TokenBudgetDetails.empty(),
                ContextUsageDetails.empty(),
                FallbackDetails.empty()
        );
    }

    public static ContextTraceTechnicalDetails build(Long conversationId,
                                                     Long messageId,
                                                     RouteDetails route,
                                                     Map<String, Long> timingsMs,
                                                     RetrievalDetails retrieval,
                                                     TokenBudgetDetails tokenBudget,
                                                     ContextUsageDetails contextUsage,
                                                     FallbackDetails fallback) {
        return new ContextTraceTechnicalDetails(
                true,
                traceId(conversationId, messageId),
                messageId,
                route,
                timingsMs,
                retrieval,
                tokenBudget,
                contextUsage,
                fallback
        );
    }

    public ContextTraceTechnicalDetails withMessageId(Long conversationId, Long resolvedMessageId) {
        return new ContextTraceTechnicalDetails(
                available,
                traceId(conversationId, resolvedMessageId),
                resolvedMessageId,
                route,
                timingsMs,
                retrieval,
                tokenBudget,
                contextUsage,
                fallback
        );
    }

    public ContextTraceTechnicalDetails withLlmCalled(Boolean llmCalled) {
        return new ContextTraceTechnicalDetails(
                available,
                traceId,
                messageId,
                route.withLlmCalled(llmCalled),
                timingsMs,
                retrieval,
                tokenBudget,
                contextUsage,
                fallback
        );
    }

    public ContextTraceTechnicalDetails withTiming(String stage, Long elapsedMs) {
        if (stage == null || stage.isBlank() || elapsedMs == null || elapsedMs < 0) {
            return this;
        }
        Map<String, Long> nextTimings = new LinkedHashMap<>(timingsMs);
        nextTimings.put(stage.trim(), elapsedMs);
        return new ContextTraceTechnicalDetails(
                available,
                traceId,
                messageId,
                route,
                nextTimings,
                retrieval,
                tokenBudget,
                contextUsage,
                fallback
        );
    }

    public static String traceId(Long conversationId, Long messageId) {
        String conversationPart = conversationId == null ? "pending" : String.valueOf(conversationId);
        String messagePart = messageId == null ? "pending" : String.valueOf(messageId);
        return "ctx-" + conversationPart + "-" + messagePart;
    }

    public record RouteDetails(
            String groundingPolicy,
            String routeDecision,
            String routeReason,
            boolean ragTriggered,
            boolean ragRequired,
            boolean noEvidence,
            Boolean llmCalled,
            boolean modelSkipped
    ) {

        public RouteDetails {
            groundingPolicy = safe(groundingPolicy);
            routeDecision = safe(routeDecision);
            routeReason = safe(routeReason);
        }

        public static RouteDetails empty() {
            return new RouteDetails("", "", "", false, false, false, null, false);
        }

        public RouteDetails withLlmCalled(Boolean resolvedLlmCalled) {
            return new RouteDetails(
                    groundingPolicy,
                    routeDecision,
                    routeReason,
                    ragTriggered,
                    ragRequired,
                    noEvidence,
                    resolvedLlmCalled,
                    modelSkipped
            );
        }
    }

    public record RetrievalDetails(
            String retrievalMode,
            String provider,
            Integer topK,
            int evidenceCount,
            Map<Long, Integer> documentHitCounts,
            boolean rerankApplied,
            String rerankModel,
            String rerankFailureReason,
            boolean multiQueryApplied,
            int queryVariantCount,
            int queryDedupeCount,
            EvidenceGateDetails evidenceGate,
            List<ScoreRow> scoreRows
    ) {

        public RetrievalDetails {
            retrievalMode = safe(retrievalMode);
            provider = safe(provider);
            documentHitCounts = documentHitCounts == null ? Map.of() : Map.copyOf(documentHitCounts);
            rerankModel = safe(rerankModel);
            rerankFailureReason = safe(rerankFailureReason);
            queryVariantCount = Math.max(0, queryVariantCount);
            queryDedupeCount = Math.max(0, queryDedupeCount);
            evidenceGate = evidenceGate == null ? EvidenceGateDetails.notRun("NOT_TRIGGERED") : evidenceGate;
            scoreRows = scoreRows == null ? List.of() : List.copyOf(scoreRows);
        }

        public static RetrievalDetails notRun(String reason) {
            return new RetrievalDetails(
                    "",
                    "",
                    null,
                    0,
                    Map.of(),
                    false,
                    "",
                    "",
                    false,
                    0,
                    0,
                    EvidenceGateDetails.notRun(reason),
                    List.of()
            );
        }

        public static RetrievalDetails fromRetrieval(KnowledgeBaseRagRetrievalResult retrieval) {
            if (retrieval == null) {
                return notRun("NOT_TRIGGERED");
            }
            Set<Integer> citationIndexes = new LinkedHashSet<>();
            retrieval.citations().forEach(citation -> citationIndexes.add(citation.index()));
            List<ScoreRow> rows = retrieval.hits().stream()
                    .map(hit -> ScoreRow.fromHit(hit, citationIndexes.contains(hit.citationIndex())))
                    .toList();
            return new RetrievalDetails(
                    retrieval.retrievalMode(),
                    retrieval.provider(),
                    retrieval.topK(),
                    retrieval.hits().size(),
                    retrieval.documentHitCounts(),
                    Boolean.TRUE.equals(retrieval.rerankApplied()),
                    retrieval.rerankModel(),
                    retrieval.rerankFailureReason(),
                    Boolean.TRUE.equals(retrieval.multiQueryApplied()),
                    retrieval.queryVariantCount(),
                    retrieval.queryDedupeCount(),
                    retrieval.noEvidence()
                            ? EvidenceGateDetails.failed("NO_RETRIEVAL_HITS")
                            : EvidenceGateDetails.passed("EVIDENCE_SELECTED"),
                    rows
            );
        }
    }

    public record EvidenceGateDetails(String status, String reason) {

        public EvidenceGateDetails {
            status = safe(status);
            reason = safe(reason);
        }

        public static EvidenceGateDetails notRun(String reason) {
            return new EvidenceGateDetails("NOT_RUN", reason);
        }

        public static EvidenceGateDetails passed(String reason) {
            return new EvidenceGateDetails("PASSED", reason);
        }

        public static EvidenceGateDetails failed(String reason) {
            return new EvidenceGateDetails("FAILED", reason);
        }
    }

    public record ScoreRow(
            Integer citationIndex,
            Long documentId,
            String documentTitle,
            Long chunkId,
            Integer chunkIndex,
            String locator,
            Double vectorScore,
            Double keywordScore,
            Double fusedScore,
            Double rerankScore,
            Double finalScore,
            boolean selectedAsCitation
    ) {

        public ScoreRow {
            documentTitle = safe(documentTitle);
            locator = safe(locator);
        }

        public static ScoreRow fromHit(KnowledgeBaseRagRetrievalHit hit, boolean selectedAsCitation) {
            if (hit == null) {
                return new ScoreRow(null, null, "", null, null, "", null, null, null, null, null, false);
            }
            return new ScoreRow(
                    hit.citationIndex(),
                    hit.documentId(),
                    hit.documentTitle(),
                    hit.chunkId(),
                    hit.chunkIndex(),
                    locator(hit),
                    hit.vectorScore(),
                    hit.keywordScore(),
                    hit.fusedScore(),
                    hit.rerankScore(),
                    hit.score(),
                    selectedAsCitation
            );
        }

        private static String locator(KnowledgeBaseRagRetrievalHit hit) {
            if (hit.sourceLocator() != null && !hit.sourceLocator().isBlank()) {
                return hit.sourceLocator();
            }
            if (hit.pageNumber() != null) {
                return "page:" + hit.pageNumber();
            }
            if (hit.sectionPath() != null && !hit.sectionPath().isBlank()) {
                return hit.sectionPath();
            }
            return "";
        }
    }

    public record TokenBudgetDetails(
            int maxPromptTokens,
            int estimatedPromptTokens,
            boolean truncated,
            List<TokenBudgetTypeSummary> byType,
            List<TokenDroppedReason> droppedReasons
    ) {

        public TokenBudgetDetails {
            byType = byType == null ? List.of() : List.copyOf(byType);
            droppedReasons = droppedReasons == null ? List.of() : List.copyOf(droppedReasons);
        }

        public static TokenBudgetDetails empty() {
            return new TokenBudgetDetails(0, 0, false, List.of(), List.of());
        }

        public static TokenBudgetDetails from(int maxPromptTokens, TokenBudgetResult result) {
            if (result == null) {
                return empty();
            }
            Map<String, MutableTokenSummary> summaries = new LinkedHashMap<>();
            for (ContextType type : ContextType.values()) {
                summaries.put(type.name(), new MutableTokenSummary(type.name()));
            }
            for (ContextItem item : result.usedItems()) {
                summaries.computeIfAbsent(item.type().name(), MutableTokenSummary::new)
                        .addUsed(item.estimatedTokens());
            }
            for (ContextItem item : result.droppedItems()) {
                summaries.computeIfAbsent(item.type().name(), MutableTokenSummary::new)
                        .addDropped(item.estimatedTokens());
            }

            List<TokenBudgetTypeSummary> byType = summaries.values().stream()
                    .filter(summary -> summary.usedCount > 0 || summary.droppedCount > 0)
                    .map(MutableTokenSummary::toRecord)
                    .toList();
            List<TokenDroppedReason> droppedReasons = new ArrayList<>();
            for (MutableTokenSummary summary : summaries.values()) {
                if (summary.droppedCount > 0) {
                    droppedReasons.add(new TokenDroppedReason(
                            summary.type,
                            summary.droppedCount,
                            "TOKEN_BUDGET_EXCEEDED"
                    ));
                }
            }
            return new TokenBudgetDetails(
                    maxPromptTokens,
                    result.estimatedPromptTokens(),
                    result.truncated(),
                    byType,
                    droppedReasons
            );
        }
    }

    public record TokenBudgetTypeSummary(
            String type,
            int usedCount,
            int usedTokens,
            int droppedCount,
            int droppedTokens
    ) {

        public TokenBudgetTypeSummary {
            type = safe(type);
        }
    }

    public record TokenDroppedReason(String type, int count, String reason) {

        public TokenDroppedReason {
            type = safe(type);
            reason = safe(reason);
        }
    }

    public record ContextUsageDetails(
            SummaryUsage summary,
            MemoryUsage memory,
            RecentUsage recent
    ) {

        public ContextUsageDetails {
            summary = summary == null ? new SummaryUsage(false) : summary;
            memory = memory == null ? new MemoryUsage(false, 0, List.of()) : memory;
            recent = recent == null ? new RecentUsage(0, 0) : recent;
        }

        public static ContextUsageDetails empty() {
            return new ContextUsageDetails(
                    new SummaryUsage(false),
                    new MemoryUsage(false, 0, List.of()),
                    new RecentUsage(0, 0)
            );
        }
    }

    public record SummaryUsage(boolean used) {
    }

    public record MemoryUsage(boolean used, int count, List<String> types) {

        public MemoryUsage {
            types = types == null ? List.of() : List.copyOf(types);
        }
    }

    public record RecentUsage(int turnCount, int messageCount) {
    }

    public record FallbackDetails(boolean used, String reason, String safeError) {

        public FallbackDetails {
            reason = safe(reason);
            safeError = safe(safeError);
        }

        public static FallbackDetails empty() {
            return new FallbackDetails(false, "", "");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class MutableTokenSummary {
        private final String type;
        private int usedCount;
        private int usedTokens;
        private int droppedCount;
        private int droppedTokens;

        private MutableTokenSummary(String type) {
            this.type = type;
        }

        private void addUsed(int tokens) {
            usedCount++;
            usedTokens += Math.max(0, tokens);
        }

        private void addDropped(int tokens) {
            droppedCount++;
            droppedTokens += Math.max(0, tokens);
        }

        private TokenBudgetTypeSummary toRecord() {
            return new TokenBudgetTypeSummary(type, usedCount, usedTokens, droppedCount, droppedTokens);
        }
    }
}
