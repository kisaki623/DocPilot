package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagPromptBuilder;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaAnswer;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.rag.RagPrompt;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.ai.service.KnowledgeBaseRagQaService;
import com.docpilot.backend.ai.service.KnowledgeBaseRagRetrievalService;
import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseRagQaServiceImpl implements KnowledgeBaseRagQaService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseRagQaServiceImpl.class);

    public static final String NO_EVIDENCE_ANSWER =
            "未在当前知识库索引中检索到足够证据，无法基于知识库回答该问题。";
    public static final String RETRIEVAL_UNAVAILABLE_ANSWER =
            "知识库 RAG 检索暂不可用，暂时无法基于知识库索引回答该问题。";
    public static final String ANSWER_GENERATION_FAILED_ANSWER =
            "知识库已检索到相关证据，但回答模型本次生成失败。请先查看下方引用证据，或稍后重试。";

    private final KnowledgeBaseRagRetrievalService retrievalService;
    private final AiAnswerService aiAnswerService;
    private final RagQaProperties ragQaProperties;
    private final KnowledgeBaseRagPromptBuilder promptBuilder;

    @Autowired
    public KnowledgeBaseRagQaServiceImpl(KnowledgeBaseRagRetrievalService retrievalService,
                                         AiAnswerService aiAnswerService,
                                         RagQaProperties ragQaProperties) {
        this(retrievalService, aiAnswerService, ragQaProperties, new KnowledgeBaseRagPromptBuilder());
    }

    public KnowledgeBaseRagQaServiceImpl(KnowledgeBaseRagRetrievalService retrievalService,
                                         AiAnswerService aiAnswerService,
                                         RagQaProperties ragQaProperties,
                                         KnowledgeBaseRagPromptBuilder promptBuilder) {
        this.retrievalService = retrievalService;
        this.aiAnswerService = aiAnswerService;
        this.ragQaProperties = ragQaProperties == null ? new RagQaProperties() : ragQaProperties;
        this.promptBuilder = promptBuilder == null ? new KnowledgeBaseRagPromptBuilder() : promptBuilder;
    }

    @Override
    public KnowledgeBaseRagQaAnswer answer(KnowledgeBaseRagQaQuery query) {
        ResolvedQaQuery resolved = validateAndResolve(query);
        KnowledgeBaseRagRetrievalResult retrieval;
        try {
            retrieval = retrievalService.retrieve(new KnowledgeBaseRagRetrievalQuery(
                    resolved.userId(),
                    resolved.knowledgeBaseId(),
                    resolved.question(),
                    resolved.topK(),
                    resolved.indexVersion(),
                    "",
                    resolved.multiQueryEnabled(),
                    resolved.maxQueryVariants()
            ));
        } catch (BusinessException ex) {
            if (isScopeException(ex)) {
                throw ex;
            }
            if (!ragQaProperties.isFallbackEnabled()) {
                throw ex;
            }
            return answer(resolved, RETRIEVAL_UNAVAILABLE_ANSWER, null, true, true,
                    "retrieval_unavailable", 0);
        } catch (RuntimeException ex) {
            if (!ragQaProperties.isFallbackEnabled()) {
                throw new BusinessException(ErrorCode.AI_CALL_FAILED, "knowledge base RAG retrieval failed");
            }
            return answer(resolved, RETRIEVAL_UNAVAILABLE_ANSWER, null, true, true,
                    "retrieval_unavailable", 0);
        }
        if (retrieval.noEvidence()) {
            return answer(resolved, NO_EVIDENCE_ANSWER, retrieval, true, true, "no_evidence", 0);
        }
        try {
            RagPrompt prompt = promptBuilder.build(
                    resolved.question(),
                    retrieval.hits(),
                    ragQaProperties.getMaxContextChars()
            );
            String answerText = aiAnswerService.answer(prompt.evidenceContext(), prompt.userPrompt());
            return answer(resolved, answerText, retrieval, false, false, "", 1);
        } catch (RuntimeException ex) {
            log.warn("Knowledge base RAG answer generation failed. userId={}, knowledgeBaseId={}, questionLength={}, hitCount={}, reason={}",
                    resolved.userId(),
                    resolved.knowledgeBaseId(),
                    resolved.question().length(),
                    retrieval.hits().size(),
                    ex.getMessage());
            if (!ragQaProperties.isFallbackEnabled()) {
                throw new BusinessException(ErrorCode.AI_CALL_FAILED, "knowledge base RAG answer generation failed");
            }
            return answer(resolved, ANSWER_GENERATION_FAILED_ANSWER, retrieval, false, true,
                    "answer_generation_failed", 1);
        }
    }

    private ResolvedQaQuery validateAndResolve(KnowledgeBaseRagQaQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "knowledge base RAG QA request must not be null");
        }
        if (query.userId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId must not be null");
        }
        if (query.knowledgeBaseId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "knowledgeBaseId must not be null");
        }
        if (query.question().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "question must not be blank");
        }
        return new ResolvedQaQuery(
                query.userId(),
                query.knowledgeBaseId(),
                query.question(),
                query.topK(),
                query.indexVersion(),
                resolveSessionId(query.sessionId()),
                query.multiQueryEnabled(),
                query.maxQueryVariants()
        );
    }

    private boolean isScopeException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return ErrorCode.BAD_REQUEST.equals(errorCode)
                || ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.equals(errorCode)
                || ErrorCode.KNOWLEDGE_BASE_FORBIDDEN.equals(errorCode)
                || ErrorCode.DOCUMENT_NOT_FOUND.equals(errorCode)
                || ErrorCode.DOCUMENT_FORBIDDEN.equals(errorCode);
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return CommonConstants.QA_DEFAULT_SESSION_ID;
        }
        return sessionId.trim();
    }

    private KnowledgeBaseRagQaAnswer answer(ResolvedQaQuery query,
                                            String answer,
                                            KnowledgeBaseRagRetrievalResult retrieval,
                                            boolean noEvidence,
                                            boolean fallbackUsed,
                                            String fallbackReason,
                                            int modelCallCount) {
        return new KnowledgeBaseRagQaAnswer(
                query.userId(),
                query.knowledgeBaseId(),
                query.question(),
                answer,
                query.sessionId(),
                retrieval,
                noEvidence,
                fallbackUsed,
                fallbackReason,
                aiAnswerService.provider(),
                aiAnswerService.model(),
                modelCallCount
        );
    }

    private record ResolvedQaQuery(
            Long userId,
            Long knowledgeBaseId,
            String question,
            Integer topK,
            Integer indexVersion,
            String sessionId,
            Boolean multiQueryEnabled,
            Integer maxQueryVariants
    ) {
    }
}
