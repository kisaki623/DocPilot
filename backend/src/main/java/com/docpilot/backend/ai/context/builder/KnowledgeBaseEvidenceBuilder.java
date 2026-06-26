package com.docpilot.backend.ai.context.builder;

import com.docpilot.backend.ai.context.ContextItem;
import com.docpilot.backend.ai.context.ContextPolicy;
import com.docpilot.backend.ai.context.ContextType;
import com.docpilot.backend.ai.context.token.TokenEstimator;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.service.KnowledgeBaseRagRetrievalService;
import com.docpilot.backend.conversation.entity.Conversation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class KnowledgeBaseEvidenceBuilder {

    private static final List<String> REQUIRED_RAG_KEYWORDS = List.of(
            "根据知识库",
            "结合知识库",
            "从知识库",
            "根据文档",
            "结合文档",
            "从文档",
            "资料里",
            "资料集",
            "文档内容",
            "knowledge base",
            "based on the document",
            "based on documents"
    );
    private static final List<String> OPTIONAL_RAG_KEYWORDS = List.of(
            "项目状态",
            "当前进度",
            "做到哪",
            "已完成",
            "路线",
            "设计文档",
            "project status",
            "roadmap",
            "current state"
    );

    private final KnowledgeBaseRagRetrievalService retrievalService;
    private final TokenEstimator tokenEstimator;

    public KnowledgeBaseEvidenceBuilder(KnowledgeBaseRagRetrievalService retrievalService,
                                        TokenEstimator tokenEstimator) {
        this.retrievalService = retrievalService;
        this.tokenEstimator = tokenEstimator;
    }

    public KnowledgeBaseEvidenceResult build(Conversation conversation,
                                             String currentMessage,
                                             ContextPolicy policy) {
        if (!policy.ragEnabled() || conversation.getBoundKnowledgeBaseId() == null) {
            return KnowledgeBaseEvidenceResult.notTriggered();
        }
        RagIntent intent = resolveIntent(currentMessage);
        if (!intent.triggered()) {
            return KnowledgeBaseEvidenceResult.notTriggered();
        }

        KnowledgeBaseRagRetrievalResult retrieval = retrievalService.retrieve(new KnowledgeBaseRagRetrievalQuery(
                conversation.getUserId(),
                conversation.getBoundKnowledgeBaseId(),
                currentMessage,
                policy.ragEvidenceMaxCount(),
                null,
                ""
        ));
        if (retrieval.noEvidence()) {
            String fallback = intent.required()
                    ? "当前知识库中没有找到足够证据，无法基于知识库回答该问题。"
                    : "";
            return new KnowledgeBaseEvidenceResult(true, intent.required(), true, fallback,
                    List.of(), retrieval.citations(), retrieval.documentHitCounts());
        }

        List<ContextItem> items = new ArrayList<>();
        int index = 1;
        for (KnowledgeBaseRagRetrievalHit hit : retrieval.hits()) {
            String evidence = evidenceBlock(index++, hit, policy.singleEvidenceMaxTokens());
            items.add(new ContextItem(
                    ContextType.RAG_EVIDENCE,
                    evidence,
                    intent.required() ? 900 : 520,
                    tokenEstimator.estimate(evidence),
                    false,
                    hit.userId(),
                    hit.vectorId(),
                    "ACTIVE",
                    Map.of(
                            "knowledgeBaseId", hit.knowledgeBaseId(),
                            "documentId", hit.documentId(),
                            "documentTitle", hit.documentTitle(),
                            "chunkIndex", hit.chunkIndex(),
                            "score", hit.score()
                    )
            ));
        }
        return new KnowledgeBaseEvidenceResult(true, intent.required(), false, "",
                items, retrieval.citations(), retrieval.documentHitCounts());
    }

    private RagIntent resolveIntent(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        for (String keyword : REQUIRED_RAG_KEYWORDS) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return new RagIntent(true, true);
            }
        }
        for (String keyword : OPTIONAL_RAG_KEYWORDS) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return new RagIntent(true, false);
            }
        }
        return new RagIntent(false, false);
    }

    private String evidenceBlock(int index, KnowledgeBaseRagRetrievalHit hit, int singleEvidenceMaxTokens) {
        int maxChars = Math.max(200, singleEvidenceMaxTokens * 4);
        String content = hit.content();
        if (content.length() > maxChars) {
            content = content.substring(0, maxChars) + "...";
        }
        return "[" + index + "] documentTitle=" + hit.documentTitle()
                + ", documentId=" + hit.documentId()
                + ", chunkIndex=" + hit.chunkIndex()
                + ", score=" + hit.score()
                + "\n" + content;
    }

    private record RagIntent(boolean triggered, boolean required) {
    }
}
