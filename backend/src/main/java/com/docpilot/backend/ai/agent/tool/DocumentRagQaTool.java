package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.rag.RagEvidenceCitation;
import com.docpilot.backend.ai.rag.RagQaAnswer;
import com.docpilot.backend.ai.rag.RagQaQuery;
import com.docpilot.backend.ai.rag.RagRetrievalHit;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.service.RagQaService;
import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentRagQaTool implements AgentTool<DocumentRagQaTool.RagQaInput, DocumentRagQaTool.RagQaResult> {

    public static final String TOOL_NAME = "rag_qa_tool";

    private static final int SUMMARY_CITATION_LIMIT = 3;

    private final RagQaService ragQaService;

    public DocumentRagQaTool(RagQaService ragQaService) {
        this.ragQaService = ragQaService;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public RagQaResult execute(RagQaInput input) {
        ValidationUtils.requireNonNull(input, "input");
        ValidationUtils.requireNonNull(input.userId(), "userId");
        ValidationUtils.requireNonNull(input.documentId(), "documentId");
        ValidationUtils.requireNonBlank(input.question(), "question");

        RagQaAnswer answer = ragQaService.answer(new RagQaQuery(
                input.userId(),
                input.documentId(),
                input.question(),
                input.topK(),
                input.indexVersion(),
                input.sessionId()
        ));
        RagRetrievalResult retrieval = answer.retrieval();
        List<RagRetrievalHit> hits = retrieval == null ? List.of() : retrieval.hits();
        List<RagEvidenceCitation> citations = retrieval == null ? List.of() : retrieval.citations();
        return new RagQaResult(
                answer.userId(),
                answer.documentId(),
                answer.question(),
                answer.answer(),
                answer.sessionId(),
                retrieval == null ? resolveTopK(input.topK()) : retrieval.topK(),
                retrieval == null ? resolveIndexVersion(input.indexVersion()) : retrieval.indexVersion(),
                hits,
                citations,
                answer.noEvidence(),
                answer.fallbackUsed(),
                answer.fallbackReason(),
                buildOutputSummary(retrieval, answer.noEvidence(), answer.fallbackUsed(), answer.fallbackReason())
        );
    }

    private String buildOutputSummary(RagRetrievalResult retrieval,
                                      boolean noEvidence,
                                      boolean fallbackUsed,
                                      String fallbackReason) {
        int topK = retrieval == null ? 0 : retrieval.topK();
        int indexVersion = retrieval == null ? 1 : retrieval.indexVersion();
        List<RagRetrievalHit> hits = retrieval == null ? List.of() : retrieval.hits();
        List<RagEvidenceCitation> citations = retrieval == null ? List.of() : retrieval.citations();
        return "topK=" + topK
                + ", indexVersion=" + indexVersion
                + ", hitCount=" + hits.size()
                + ", citationCount=" + citations.size()
                + ", noEvidence=" + noEvidence
                + ", fallbackUsed=" + fallbackUsed
                + ", fallbackReason=" + safeText(fallbackReason)
                + ", citationRefs=" + citationRefs(citations);
    }

    private String citationRefs(List<RagEvidenceCitation> citations) {
        if (citations == null || citations.isEmpty()) {
            return "[]";
        }
        List<String> refs = new ArrayList<>();
        int limit = Math.min(citations.size(), SUMMARY_CITATION_LIMIT);
        for (int i = 0; i < limit; i++) {
            RagEvidenceCitation citation = citations.get(i);
            refs.add("[" + citation.index()
                    + ":chunkId=" + safeText(citation.chunkId())
                    + ",score=" + String.format(java.util.Locale.ROOT, "%.4f", citation.score())
                    + "]");
        }
        return refs.toString();
    }

    private int resolveTopK(Integer topK) {
        return topK == null || topK <= 0 ? 0 : topK;
    }

    private int resolveIndexVersion(Integer indexVersion) {
        return indexVersion == null || indexVersion <= 0 ? 1 : indexVersion;
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record RagQaInput(Long userId,
                             Long documentId,
                             String question,
                             String sessionId,
                             Integer topK,
                             Integer indexVersion) {
    }

    public record RagQaResult(Long userId,
                              Long documentId,
                              String question,
                              String answer,
                              String sessionId,
                              int topK,
                              int indexVersion,
                              List<RagRetrievalHit> retrievalHits,
                              List<RagEvidenceCitation> citations,
                              boolean noEvidence,
                              boolean fallbackUsed,
                              String fallbackReason,
                              String outputSummary) {

        public RagQaResult {
            answer = answer == null ? "" : answer.trim();
            sessionId = sessionId == null ? "" : sessionId.trim();
            retrievalHits = retrievalHits == null ? List.of() : List.copyOf(retrievalHits);
            citations = citations == null ? List.of() : List.copyOf(citations);
            fallbackReason = fallbackReason == null ? "" : fallbackReason.trim();
            outputSummary = outputSummary == null ? "" : outputSummary.trim();
        }
    }
}
