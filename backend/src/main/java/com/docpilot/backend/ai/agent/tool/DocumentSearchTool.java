package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.rag.RagEvidenceCitation;
import com.docpilot.backend.ai.rag.RagRetrievalHit;
import com.docpilot.backend.ai.rag.RagRetrievalQuery;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.service.RagDocumentRetrievalService;
import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentSearchTool implements AgentTool<DocumentSearchTool.SearchInput, DocumentSearchTool.SearchResult> {

    public static final String TOOL_NAME = "document_search_tool";

    private static final int TEXT_PREVIEW_MAX_LENGTH = 180;

    private final RagDocumentRetrievalService retrievalService;

    public DocumentSearchTool(RagDocumentRetrievalService retrievalService) {
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
        ValidationUtils.requireNonNull(input.documentId(), "documentId");
        ValidationUtils.requireNonBlank(input.query(), "query");

        RagRetrievalResult retrieval = retrievalService.retrieve(new RagRetrievalQuery(
                input.userId(),
                input.documentId(),
                input.query(),
                input.topK(),
                input.indexVersion(),
                ""
        ));
        List<SearchHit> hits = retrieval.hits().stream()
                .map(DocumentSearchTool::toSafeHit)
                .toList();
        List<SearchCitation> citations = retrieval.citations().stream()
                .map(DocumentSearchTool::toSafeCitation)
                .toList();
        return new SearchResult(
                retrieval.userId(),
                retrieval.documentId(),
                retrieval.query(),
                retrieval.topK(),
                retrieval.indexVersion(),
                retrieval.noEvidence(),
                hits.size(),
                citations.size(),
                hits,
                citations,
                buildOutputSummary(retrieval, hits.size(), citations.size())
        );
    }

    private static SearchHit toSafeHit(RagRetrievalHit hit) {
        return new SearchHit(
                hit.citationIndex(),
                hit.score(),
                hit.sourceName(),
                hit.chunkId(),
                hit.chunkIndex(),
                hit.pageNumber(),
                hit.sectionPath(),
                hit.sourceLocator(),
                hit.blockType(),
                truncate(hit.quoteText()),
                truncate(hit.snippet()),
                hit.contentHash()
        );
    }

    private static SearchCitation toSafeCitation(RagEvidenceCitation citation) {
        return new SearchCitation(
                citation.index(),
                citation.documentId(),
                citation.sourceName(),
                citation.indexVersion(),
                citation.chunkId(),
                citation.chunkIndex(),
                citation.pageNumber(),
                citation.sectionPath(),
                citation.sourceLocator(),
                citation.blockType(),
                truncate(citation.quoteText()),
                truncate(citation.snippet()),
                citation.contentHash(),
                citation.score()
        );
    }

    private static String buildOutputSummary(RagRetrievalResult retrieval, int hitCount, int citationCount) {
        return "topK=" + retrieval.topK()
                + ", indexVersion=" + retrieval.indexVersion()
                + ", hitCount=" + hitCount
                + ", citationCount=" + citationCount
                + ", noEvidence=" + retrieval.noEvidence()
                + ", provider=" + safeText(retrieval.provider());
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
                              Long documentId,
                              String query,
                              Integer topK,
                              Integer indexVersion) {
    }

    public record SearchResult(Long userId,
                               Long documentId,
                               String query,
                               int topK,
                               int indexVersion,
                               boolean noEvidence,
                               int hitCount,
                               int citationCount,
                               List<SearchHit> hits,
                               List<SearchCitation> citations,
                               String outputSummary) {

        public SearchResult {
            query = query == null ? "" : query.trim();
            hits = hits == null ? List.of() : List.copyOf(hits);
            citations = citations == null ? List.of() : List.copyOf(citations);
            outputSummary = outputSummary == null ? "" : outputSummary.trim();
        }
    }

    public record SearchHit(int rank,
                            double score,
                            String sourceName,
                            Long chunkId,
                            Integer chunkIndex,
                            Integer pageNumber,
                            String sectionPath,
                            String sourceLocator,
                            String blockType,
                            String quoteText,
                            String snippet,
                            String contentHash) {

        public SearchHit {
            sourceName = safeText(sourceName);
            sectionPath = safeText(sectionPath);
            sourceLocator = safeText(sourceLocator);
            blockType = safeText(blockType);
            quoteText = truncate(quoteText);
            snippet = truncate(snippet);
            contentHash = safeText(contentHash);
        }
    }

    public record SearchCitation(int index,
                                 Long documentId,
                                 String sourceName,
                                 Integer indexVersion,
                                 Long chunkId,
                                 Integer chunkIndex,
                                 Integer pageNumber,
                                 String sectionPath,
                                 String sourceLocator,
                                 String blockType,
                                 String quoteText,
                                 String snippet,
                                 String contentHash,
                                 double score) {

        public SearchCitation {
            sourceName = safeText(sourceName);
            sectionPath = safeText(sectionPath);
            sourceLocator = safeText(sourceLocator);
            blockType = safeText(blockType);
            quoteText = truncate(quoteText);
            snippet = truncate(snippet);
            contentHash = safeText(contentHash);
        }
    }
}
