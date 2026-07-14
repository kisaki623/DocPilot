package com.docpilot.backend.ai.context.builder;

import com.docpilot.backend.ai.context.ContextItem;
import com.docpilot.backend.ai.context.ContextPolicy;
import com.docpilot.backend.ai.context.ContextTraceTechnicalDetails;
import com.docpilot.backend.ai.context.ContextType;
import com.docpilot.backend.ai.context.GroundingPolicy;
import com.docpilot.backend.ai.context.RouteDecision;
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
            "基于知识库",
            "结合知识库",
            "从知识库",
            "只根据知识库",
            "只依据知识库",
            "仅根据知识库",
            "仅依据知识库",
            "根据文档",
            "基于文档",
            "结合文档",
            "从文档",
            "请引用文档",
            "引用文档",
            "请引用材料",
            "引用材料",
            "资料里",
            "文档内容",
            "based on the knowledge base",
            "based on the document",
            "based on documents",
            "according to the knowledge base",
            "only use the knowledge base",
            "use only the knowledge base",
            "cite the document",
            "cite documents"
    );
    private static final List<String> MODEL_ONLY_EXACT_MESSAGES = List.of(
            "你好",
            "您好",
            "hello",
            "hi",
            "hey",
            "谢谢",
            "thanks",
            "thank you",
            "你是谁",
            "你能做什么",
            "who are you",
            "what can you do"
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
        GroundingPolicy groundingPolicy = policy.ragEnabled() ? GroundingPolicy.AUTO_RAG : GroundingPolicy.MODEL_ONLY;
        return build(conversation, currentMessage, policy, groundingPolicy);
    }

    public KnowledgeBaseEvidenceResult build(Conversation conversation,
                                             String currentMessage,
                                             ContextPolicy policy,
                                             GroundingPolicy groundingPolicy) {
        if (!policy.ragEnabled() || conversation.getBoundKnowledgeBaseId() == null) {
            RouteDecision routeDecision = groundingPolicy == GroundingPolicy.AUTO_RAG
                    ? RouteDecision.AUTO_NO_KB_MODEL
                    : RouteDecision.MODEL_ONLY;
            return KnowledgeBaseEvidenceResult.notTriggered(routeDecision);
        }

        if (groundingPolicy == GroundingPolicy.MODEL_ONLY) {
            return KnowledgeBaseEvidenceResult.notTriggered(RouteDecision.MODEL_ONLY);
        }

        RagIntent intent = resolveIntent(currentMessage);
        if (groundingPolicy == GroundingPolicy.STRICT_KB) {
            intent = new RagIntent(true, true);
        } else if (!intent.triggered()) {
            return KnowledgeBaseEvidenceResult.notTriggered(RouteDecision.AUTO_INTENT_NOT_TRIGGERED_MODEL);
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
            ContextTraceTechnicalDetails.RetrievalDetails retrievalDetails =
                    ContextTraceTechnicalDetails.RetrievalDetails.fromRetrieval(retrieval);
            boolean requiredNoEvidence = intent.required();
            String fallback = requiredNoEvidence
                    ? "当前知识库中没有找到足够证据，无法基于知识库回答该问题。"
                    : "";
            RouteDecision routeDecision;
            if (groundingPolicy == GroundingPolicy.STRICT_KB) {
                routeDecision = RouteDecision.STRICT_NO_EVIDENCE_FALLBACK;
            } else if (requiredNoEvidence) {
                routeDecision = RouteDecision.AUTO_REQUIRED_NO_EVIDENCE_FALLBACK;
            } else {
                routeDecision = RouteDecision.AUTO_NO_EVIDENCE_MODEL;
            }
            return new KnowledgeBaseEvidenceResult(true, requiredNoEvidence, true, fallback,
                    List.of(), retrieval.citations(), retrieval.documentHitCounts(), routeDecision, retrievalDetails);
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
        RouteDecision routeDecision = groundingPolicy == GroundingPolicy.STRICT_KB
                ? RouteDecision.STRICT_KB_EVIDENCE
                : RouteDecision.AUTO_RAG_EVIDENCE;
        ContextTraceTechnicalDetails.RetrievalDetails retrievalDetails =
                ContextTraceTechnicalDetails.RetrievalDetails.fromRetrieval(retrieval);
        return new KnowledgeBaseEvidenceResult(true, intent.required(), false, "",
                items, retrieval.citations(), retrieval.documentHitCounts(), routeDecision, retrievalDetails);
    }

    private RagIntent resolveIntent(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        for (String keyword : REQUIRED_RAG_KEYWORDS) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return new RagIntent(true, true);
            }
        }
        if (isObviousModelOnlyMessage(normalized)) {
            return new RagIntent(false, false);
        }
        return new RagIntent(true, false);
    }

    private boolean isObviousModelOnlyMessage(String normalizedMessage) {
        String compact = normalizedMessage == null ? "" : normalizedMessage
                .replaceAll("\\s+", "")
                .replace("？", "?")
                .replace("！", "!")
                .replace("。", "")
                .replace(".", "")
                .trim();
        if (compact.isBlank()) {
            return true;
        }
        for (String message : MODEL_ONLY_EXACT_MESSAGES) {
            String normalized = message.toLowerCase(Locale.ROOT)
                    .replaceAll("\\s+", "")
                    .replace("？", "?")
                    .replace("！", "!")
                    .replace("。", "")
                    .replace(".", "")
                    .trim();
            if (compact.equals(normalized) || compact.equals(normalized + "?") || compact.equals(normalized + "!")) {
                return true;
            }
        }
        return false;
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
