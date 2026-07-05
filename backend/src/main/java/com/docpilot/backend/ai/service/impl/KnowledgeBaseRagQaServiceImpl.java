package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagPromptBuilder;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class KnowledgeBaseRagQaServiceImpl implements KnowledgeBaseRagQaService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseRagQaServiceImpl.class);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
    private static final double LOW_CONFIDENCE_CITATION_SCORE_FLOOR = 0.05D;
    private static final double LOW_CONFIDENCE_CITATION_MAX_SCORE_GATE = 0.5D;

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
            return answer(resolved, answerText, withAnswerAwareCitations(retrieval, answerText, resolved.question()),
                    false, false, "", 1);
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

    private KnowledgeBaseRagRetrievalResult withAnswerAwareCitations(KnowledgeBaseRagRetrievalResult retrieval,
                                                                     String answer,
                                                                     String question) {
        if (retrieval == null || retrieval.noEvidence() || retrieval.citations().size() <= 1) {
            return retrieval;
        }
        List<KnowledgeBaseRagEvidenceCitation> originalCitations = retrieval.citations();
        List<KnowledgeBaseRagEvidenceCitation> filtered = originalCitations;
        Set<String> answerNumbers = extractNumbers(answer);
        if (!answerNumbers.isEmpty()) {
            boolean hasNumericSupport = originalCitations.stream()
                    .map(this::extractNumbers)
                    .anyMatch(numbers -> intersects(numbers, answerNumbers));
            if (hasNumericSupport) {
                List<KnowledgeBaseRagEvidenceCitation> numericFiltered = originalCitations.stream()
                        .filter(citation -> {
                            Set<String> citationNumbers = extractNumbers(citation);
                            return citationNumbers.isEmpty() || intersects(citationNumbers, answerNumbers);
                        })
                        .toList();
                if (canUseFilteredCitations(originalCitations, numericFiltered, question)) {
                    filtered = numericFiltered;
                }
            }
        }

        filtered = pruneLowConfidenceMultiDocumentCitations(originalCitations, filtered, question);
        if (filtered.size() == originalCitations.size()) {
            return retrieval;
        }
        return new KnowledgeBaseRagRetrievalResult(
                retrieval.userId(),
                retrieval.knowledgeBaseId(),
                retrieval.query(),
                retrieval.topK(),
                retrieval.indexVersion(),
                retrieval.documentIds(),
                retrieval.hits(),
                filtered,
                retrieval.noEvidence(),
                retrieval.provider(),
                retrieval.collection(),
                retrieval.embeddingModel(),
                retrieval.documentHitCounts(),
                retrieval.retrievalMode(),
                retrieval.rerankApplied(),
                retrieval.rerankModel(),
                retrieval.multiQueryApplied(),
                retrieval.queryVariantCount(),
                retrieval.queryDedupeCount()
        );
    }

    private boolean canUseFilteredCitations(List<KnowledgeBaseRagEvidenceCitation> originalCitations,
                                            List<KnowledgeBaseRagEvidenceCitation> filtered,
                                            String question) {
        if (filtered.isEmpty() || filtered.size() == originalCitations.size()) {
            return false;
        }
        return !isMultiDocumentIntent(question)
                || distinctCitationDocumentCount(filtered) >= Math.min(2, distinctCitationDocumentCount(originalCitations));
    }

    private List<KnowledgeBaseRagEvidenceCitation> pruneLowConfidenceMultiDocumentCitations(
            List<KnowledgeBaseRagEvidenceCitation> originalCitations,
            List<KnowledgeBaseRagEvidenceCitation> currentCitations,
            String question) {
        if (!isMultiDocumentIntent(question) || currentCitations.size() <= 2) {
            return currentCitations;
        }
        double maxScore = currentCitations.stream()
                .mapToDouble(KnowledgeBaseRagEvidenceCitation::score)
                .max()
                .orElse(0D);
        if (maxScore < LOW_CONFIDENCE_CITATION_MAX_SCORE_GATE) {
            return currentCitations;
        }
        List<KnowledgeBaseRagEvidenceCitation> pruned = currentCitations.stream()
                .filter(citation -> citation.score() >= LOW_CONFIDENCE_CITATION_SCORE_FLOOR)
                .toList();
        if (!canUseFilteredCitations(originalCitations, pruned, question)) {
            return currentCitations;
        }
        return pruned;
    }

    private boolean isMultiDocumentIntent(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = question.toLowerCase();
        return normalized.contains("compare")
                || normalized.contains("summarize")
                || normalized.contains("summary")
                || normalized.contains("both")
                || normalized.contains("比较")
                || normalized.contains("对比")
                || normalized.contains("总结")
                || normalized.contains("两份")
                || normalized.contains("两个");
    }

    private int distinctCitationDocumentCount(List<KnowledgeBaseRagEvidenceCitation> citations) {
        return citations.stream()
                .map(KnowledgeBaseRagEvidenceCitation::documentId)
                .collect(java.util.stream.Collectors.toSet())
                .size();
    }

    private Set<String> extractNumbers(KnowledgeBaseRagEvidenceCitation citation) {
        if (citation == null) {
            return Set.of();
        }
        return extractNumbers(citation.quoteText() + " " + citation.snippet());
    }

    private Set<String> extractNumbers(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        Set<String> numbers = new LinkedHashSet<>();
        while (matcher.find()) {
            String number = matcher.group();
            if (number.replace(".", "").length() <= 4) {
                numbers.add(number);
            }
        }
        return numbers;
    }

    private boolean intersects(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        for (String value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
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
