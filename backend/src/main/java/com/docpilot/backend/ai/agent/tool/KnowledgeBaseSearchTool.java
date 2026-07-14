package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.service.KnowledgeBaseRagRetrievalService;
import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KnowledgeBaseSearchTool implements AgentTool<KnowledgeBaseSearchTool.SearchInput, KnowledgeBaseSearchTool.SearchResult> {

    public static final String TOOL_NAME = "knowledge_base_search_tool";

    private static final int TEXT_PREVIEW_MAX_LENGTH = 180;

    private final KnowledgeBaseRagRetrievalService retrievalService;

    public KnowledgeBaseSearchTool(KnowledgeBaseRagRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public SearchResult execute(SearchInput input) {
        ValidationUtils.requireNonNull(input, "input");
        ValidationUtils.requireNonNull(input.userId(), "userId");
        ValidationUtils.requireNonNull(input.knowledgeBaseId(), "knowledgeBaseId");
        ValidationUtils.requireNonBlank(input.query(), "query");

        KnowledgeBaseRagRetrievalResult retrieval = retrievalService.retrieve(new KnowledgeBaseRagRetrievalQuery(
                input.userId(),
                input.knowledgeBaseId(),
                input.query(),
                input.topK(),
                input.indexVersion(),
                "",
                input.multiQueryEnabled(),
                input.maxQueryVariants()
        ));
        List<SearchHit> hits = retrieval.hits().stream()
                .map(KnowledgeBaseSearchTool::toSafeHit)
                .toList();
        List<SearchCitation> citations = retrieval.citations().stream()
                .map(KnowledgeBaseSearchTool::toSafeCitation)
                .toList();
        return new SearchResult(
                retrieval.userId(),
                retrieval.knowledgeBaseId(),
                retrieval.query(),
                retrieval.topK(),
                retrieval.indexVersion(),
                retrieval.documentIds(),
                retrieval.documentHitCounts(),
                retrieval.noEvidence(),
                hits.size(),
                citations.size(),
                safeText(retrieval.retrievalMode()),
                retrieval.rerankApplied(),
                safeText(retrieval.rerankModel()),
                safeText(retrieval.rerankFailureReason()),
                retrieval.multiQueryApplied(),
                retrieval.queryVariantCount(),
                retrieval.queryDedupeCount(),
                hits,
                citations,
                buildOutputSummary(retrieval, hits.size(), citations.size())
        );
    }

    private static SearchHit toSafeHit(KnowledgeBaseRagRetrievalHit hit) {
        return new SearchHit(
                hit.citationIndex(),
                hit.score(),
                hit.documentId(),
                hit.documentTitle(),
                hit.chunkId(),
                hit.chunkIndex(),
                truncate(hit.quoteText()),
                truncate(hit.snippet()),
                hit.contentHash(),
                hit.sectionPath(),
                hit.structureType(),
                hit.pageNumber(),
                hit.sourceLocator(),
                hit.blockType(),
                hit.vectorScore(),
                hit.keywordScore(),
                hit.fusedScore(),
                hit.rerankScore()
        );
    }

    private static SearchCitation toSafeCitation(KnowledgeBaseRagEvidenceCitation citation) {
        return new SearchCitation(
                citation.index(),
                citation.knowledgeBaseId(),
                citation.documentId(),
                citation.documentTitle(),
                citation.indexVersion(),
                citation.chunkId(),
                citation.chunkIndex(),
                truncate(citation.quoteText()),
                truncate(citation.snippet()),
                citation.contentHash(),
                citation.sectionPath(),
                citation.structureType(),
                citation.pageNumber(),
                citation.sourceLocator(),
                citation.blockType(),
                citation.score(),
                citation.vectorScore(),
                citation.keywordScore(),
                citation.fusedScore(),
                citation.rerankScore()
        );
    }

    private static String buildOutputSummary(KnowledgeBaseRagRetrievalResult retrieval, int hitCount, int citationCount) {
        return "topK=" + retrieval.topK()
                + ", indexVersion=" + retrieval.indexVersion()
                + ", documentCount=" + retrieval.documentIds().size()
                + ", hitCount=" + hitCount
                + ", citationCount=" + citationCount
                + ", noEvidence=" + retrieval.noEvidence()
                + ", retrievalMode=" + safeText(retrieval.retrievalMode())
                + ", rerankApplied=" + retrieval.rerankApplied()
                + rerankFailureSummary(retrieval.rerankFailureReason())
                + ", multiQueryApplied=" + retrieval.multiQueryApplied();
    }

    private static String rerankFailureSummary(String failureReason) {
        String reason = safeText(failureReason);
        return reason.isBlank() ? "" : ", rerankFailureReason=" + reason;
    }

    private static String truncate(String value) {
        String text = safeText(value);
        if (text.length() <= TEXT_PREVIEW_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, TEXT_PREVIEW_MAX_LENGTH) + "...";
    }

    private static String safeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record SearchInput(Long userId,
                              Long knowledgeBaseId,
                              String query,
                              Integer topK,
                              Integer indexVersion,
                              Boolean multiQueryEnabled,
                              Integer maxQueryVariants) {
    }

    public record SearchResult(Long userId,
                               Long knowledgeBaseId,
                               String query,
                               int topK,
                               int indexVersion,
                               List<Long> documentIds,
                               Map<Long, Integer> documentHitCounts,
                               boolean noEvidence,
                               int hitCount,
                               int citationCount,
                               String retrievalMode,
                               boolean rerankApplied,
                               String rerankModel,
                               String rerankFailureReason,
                               boolean multiQueryApplied,
                               int queryVariantCount,
                               int queryDedupeCount,
                               List<SearchHit> hits,
                               List<SearchCitation> citations,
                               String outputSummary) {

        public SearchResult(Long userId,
                            Long knowledgeBaseId,
                            String query,
                            int topK,
                            int indexVersion,
                            List<Long> documentIds,
                            Map<Long, Integer> documentHitCounts,
                            boolean noEvidence,
                            int hitCount,
                            int citationCount,
                            String retrievalMode,
                            boolean rerankApplied,
                            String rerankModel,
                            boolean multiQueryApplied,
                            int queryVariantCount,
                            int queryDedupeCount,
                            List<SearchHit> hits,
                            List<SearchCitation> citations,
                            String outputSummary) {
            this(userId, knowledgeBaseId, query, topK, indexVersion, documentIds, documentHitCounts,
                    noEvidence, hitCount, citationCount, retrievalMode, rerankApplied, rerankModel, "",
                    multiQueryApplied, queryVariantCount, queryDedupeCount, hits, citations, outputSummary);
        }

        public SearchResult {
            query = safeText(query);
            documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
            documentHitCounts = documentHitCounts == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(documentHitCounts));
            retrievalMode = safeText(retrievalMode);
            rerankModel = safeText(rerankModel);
            rerankFailureReason = safeText(rerankFailureReason);
            hits = hits == null ? List.of() : List.copyOf(hits);
            citations = citations == null ? List.of() : List.copyOf(citations);
            outputSummary = safeText(outputSummary);
        }
    }

    public record SearchHit(int rank,
                            double score,
                            Long documentId,
                            String documentTitle,
                            Long chunkId,
                            Integer chunkIndex,
                            String quoteText,
                            String snippet,
                            String contentHash,
                            String sectionPath,
                            String structureType,
                            Integer pageNumber,
                            String sourceLocator,
                            String blockType,
                            Double vectorScore,
                            Double keywordScore,
                            Double fusedScore,
                            Double rerankScore) {

        public SearchHit(int rank,
                         double score,
                         Long documentId,
                         String documentTitle,
                         Long chunkId,
                         Integer chunkIndex,
                         String quoteText,
                         String snippet,
                         String contentHash,
                         Double vectorScore,
                         Double keywordScore,
                         Double fusedScore,
                         Double rerankScore) {
            this(rank, score, documentId, documentTitle, chunkId, chunkIndex, quoteText, snippet, contentHash,
                    "", "", null, "", "", vectorScore, keywordScore, fusedScore, rerankScore);
        }

        public SearchHit {
            documentTitle = safeText(documentTitle);
            quoteText = truncate(quoteText);
            snippet = truncate(snippet);
            contentHash = safeText(contentHash);
            sectionPath = safeText(sectionPath);
            structureType = safeText(structureType);
            sourceLocator = safeText(sourceLocator);
            blockType = safeText(blockType);
        }
    }

    public record SearchCitation(int index,
                                 Long knowledgeBaseId,
                                 Long documentId,
                                 String documentTitle,
                                 Integer indexVersion,
                                 Long chunkId,
                                 Integer chunkIndex,
                                 String quoteText,
                                 String snippet,
                                 String contentHash,
                                 String sectionPath,
                                 String structureType,
                                 Integer pageNumber,
                                 String sourceLocator,
                                 String blockType,
                                 double score,
                                 Double vectorScore,
                                 Double keywordScore,
                                 Double fusedScore,
                                 Double rerankScore) {

        public SearchCitation(int index,
                              Long knowledgeBaseId,
                              Long documentId,
                              String documentTitle,
                              Integer indexVersion,
                              Long chunkId,
                              Integer chunkIndex,
                              String quoteText,
                              String snippet,
                              String contentHash,
                              double score,
                              Double vectorScore,
                              Double keywordScore,
                              Double fusedScore,
                              Double rerankScore) {
            this(index, knowledgeBaseId, documentId, documentTitle, indexVersion, chunkId, chunkIndex,
                    quoteText, snippet, contentHash, "", "", null, "", "", score, vectorScore, keywordScore,
                    fusedScore, rerankScore);
        }

        public SearchCitation {
            documentTitle = safeText(documentTitle);
            quoteText = truncate(quoteText);
            snippet = truncate(snippet);
            contentHash = safeText(contentHash);
            sectionPath = safeText(sectionPath);
            structureType = safeText(structureType);
            sourceLocator = safeText(sourceLocator);
            blockType = safeText(blockType);
        }
    }
}
