package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.entity.DocumentQaHistory;
import com.docpilot.backend.ai.mapper.DocumentQaHistoryMapper;
import com.docpilot.backend.ai.rag.RagPrompt;
import com.docpilot.backend.ai.rag.RagPromptBuilder;
import com.docpilot.backend.ai.rag.RagQaAnswer;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.rag.RagQaQuery;
import com.docpilot.backend.ai.rag.RagRetrievalQuery;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.ai.service.RagDocumentRetrievalService;
import com.docpilot.backend.ai.service.RagQaService;
import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class RagQaServiceImpl implements RagQaService {

    public static final String NO_EVIDENCE_ANSWER =
            "未在当前文档索引中检索到足够证据，无法基于文档回答该问题。请确认文档已完成 RAG 索引，或换一个更具体的问题。";
    public static final String RETRIEVAL_UNAVAILABLE_ANSWER =
            "RAG 检索暂不可用，暂时无法基于文档索引回答该问题。请稍后重试。";

    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final RagDocumentRetrievalService retrievalService;
    private final AiAnswerService aiAnswerService;
    private final DocumentQaHistoryMapper documentQaHistoryMapper;
    private final RagQaProperties ragQaProperties;
    private final RagPromptBuilder promptBuilder;

    @Autowired
    public RagQaServiceImpl(RagDocumentRetrievalService retrievalService,
                            AiAnswerService aiAnswerService,
                            DocumentQaHistoryMapper documentQaHistoryMapper,
                            RagQaProperties ragQaProperties) {
        this(retrievalService, aiAnswerService, documentQaHistoryMapper, ragQaProperties, new RagPromptBuilder());
    }

    public RagQaServiceImpl(RagDocumentRetrievalService retrievalService,
                            AiAnswerService aiAnswerService,
                            DocumentQaHistoryMapper documentQaHistoryMapper,
                            RagQaProperties ragQaProperties,
                            RagPromptBuilder promptBuilder) {
        this.retrievalService = retrievalService;
        this.aiAnswerService = aiAnswerService;
        this.documentQaHistoryMapper = documentQaHistoryMapper;
        this.ragQaProperties = ragQaProperties == null ? new RagQaProperties() : ragQaProperties;
        this.promptBuilder = promptBuilder == null ? new RagPromptBuilder() : promptBuilder;
    }

    @Override
    public RagQaAnswer answer(RagQaQuery query) {
        ResolvedQaQuery resolved = validateAndResolve(query);
        RagRetrievalResult retrieval;
        try {
            retrieval = retrieve(resolved);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (!ragQaProperties.isFallbackEnabled()) {
                throw new BusinessException(ErrorCode.AI_CALL_FAILED, "RAG retrieval failed");
            }
            RagQaAnswer answer = answer(resolved, RETRIEVAL_UNAVAILABLE_ANSWER, null, true, true,
                    "retrieval_unavailable");
            saveHistory(answer);
            return answer;
        }
        if (retrieval.noEvidence()) {
            RagQaAnswer answer = answer(resolved, NO_EVIDENCE_ANSWER, retrieval, true, true, "no_evidence");
            saveHistory(answer);
            return answer;
        }
        try {
            RagPrompt prompt = promptBuilder.build(
                    resolved.question(),
                    retrieval.hits(),
                    Map.of(),
                    ragQaProperties.getMaxContextChars()
            );
            String answerText = aiAnswerService.answer(prompt.evidenceContext(), prompt.userPrompt());
            RagQaAnswer answer = answer(resolved, answerText, retrieval, false, false, "");
            saveHistory(answer);
            return answer;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, "RAG answer generation failed");
        }
    }

    @Override
    public SseEmitter streamAnswer(RagQaQuery query) {
        SseEmitter emitter = createEmitter();
        startStreamWorker(() -> doStreamAnswer(query, emitter));
        return emitter;
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter(SSE_TIMEOUT_MS);
    }

    protected void startStreamWorker(Runnable task) {
        new Thread(task, "rag-qa-sse").start();
    }

    private void doStreamAnswer(RagQaQuery query, SseEmitter emitter) {
        ResolvedQaQuery resolved = null;
        StringBuilder answerBuffer = new StringBuilder();
        try {
            resolved = validateAndResolve(query);
            send(emitter, "meta", Map.of(
                    "documentId", resolved.documentId(),
                    "sessionId", resolved.sessionId(),
                    "topK", resolved.topK() == null ? ragQaProperties.getTopK() : Math.min(resolved.topK(), RagDocumentRetrievalServiceImpl.MAX_TOP_K),
                    "indexVersion", resolved.indexVersion() == null ? RagDocumentRetrievalServiceImpl.DEFAULT_INDEX_VERSION : resolved.indexVersion()
            ));
            RagRetrievalResult retrieval = retrieve(resolved);
            send(emitter, "retrieval", retrievalSummary(retrieval));
            retrieval.citations().forEach(citation -> send(emitter, "citation", citation));
            if (retrieval.noEvidence()) {
                answerBuffer.append(NO_EVIDENCE_ANSWER);
                send(emitter, "chunk", NO_EVIDENCE_ANSWER);
                saveHistory(answer(resolved, answerBuffer.toString(), retrieval, true, true, "no_evidence"));
                send(emitter, "done", donePayload(resolved, retrieval, true));
                emitter.complete();
                return;
            }
            RagPrompt prompt = promptBuilder.build(
                    resolved.question(),
                    retrieval.hits(),
                    Map.of(),
                    ragQaProperties.getMaxContextChars()
            );
            Consumer<String> chunkConsumer = chunk -> {
                if (chunk == null || chunk.isEmpty()) {
                    return;
                }
                answerBuffer.append(chunk);
                send(emitter, "chunk", chunk);
            };
            aiAnswerService.streamAnswer(prompt.evidenceContext(), prompt.userPrompt(), chunkConsumer);
            saveHistory(answer(resolved, answerBuffer.toString(), retrieval, false, false, ""));
            send(emitter, "done", donePayload(resolved, retrieval, false));
            emitter.complete();
        } catch (RuntimeException ex) {
            if (resolved != null && ragQaProperties.isFallbackEnabled()) {
                answerBuffer.setLength(0);
                answerBuffer.append(RETRIEVAL_UNAVAILABLE_ANSWER);
                send(emitter, "error", Map.of("message", "RAG 检索暂不可用", "stage", "retrieval"));
                send(emitter, "chunk", RETRIEVAL_UNAVAILABLE_ANSWER);
                saveHistory(answer(resolved, answerBuffer.toString(), null, true, true, "retrieval_unavailable"));
                send(emitter, "done", Map.of(
                        "documentId", resolved.documentId(),
                        "sessionId", resolved.sessionId(),
                        "noEvidence", true,
                        "citationCount", 0
                ));
                emitter.complete();
                return;
            }
            send(emitter, "error", Map.of("message", "RAG QA failed", "stage", "unknown"));
            emitter.completeWithError(ex);
        }
    }

    private RagRetrievalResult retrieve(ResolvedQaQuery query) {
        return retrievalService.retrieve(new RagRetrievalQuery(
                query.userId(),
                query.documentId(),
                query.question(),
                query.topK(),
                query.indexVersion(),
                ""
        ));
    }

    private ResolvedQaQuery validateAndResolve(RagQaQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "RAG QA request must not be null");
        }
        if (query.userId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId must not be null");
        }
        if (query.documentId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "documentId must not be null");
        }
        if (query.question().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "question must not be blank");
        }
        return new ResolvedQaQuery(
                query.userId(),
                query.documentId(),
                query.question(),
                query.topK(),
                query.indexVersion(),
                resolveSessionId(query.sessionId())
        );
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return CommonConstants.QA_DEFAULT_SESSION_ID;
        }
        return sessionId.trim();
    }

    private RagQaAnswer answer(ResolvedQaQuery query,
                               String answer,
                               RagRetrievalResult retrieval,
                               boolean noEvidence,
                               boolean fallbackUsed,
                               String fallbackReason) {
        return new RagQaAnswer(
                query.userId(),
                query.documentId(),
                query.question(),
                answer,
                query.sessionId(),
                retrieval,
                noEvidence,
                fallbackUsed,
                fallbackReason
        );
    }

    private void saveHistory(RagQaAnswer answer) {
        DocumentQaHistory history = new DocumentQaHistory();
        history.setUserId(answer.userId());
        history.setDocumentId(answer.documentId());
        history.setQuestion(answer.question());
        history.setAnswer(answer.answer());
        LocalDateTime now = LocalDateTime.now();
        history.setCreateTime(now);
        history.setUpdateTime(now);
        documentQaHistoryMapper.insert(history);
    }

    private Map<String, Object> retrievalSummary(RagRetrievalResult retrieval) {
        return Map.of(
                "documentId", retrieval.documentId(),
                "indexVersion", retrieval.indexVersion(),
                "topK", retrieval.topK(),
                "hitCount", retrieval.hits().size(),
                "noEvidence", retrieval.noEvidence(),
                "provider", retrieval.provider(),
                "collection", retrieval.collection()
        );
    }

    private Map<String, Object> donePayload(ResolvedQaQuery query, RagRetrievalResult retrieval, boolean noEvidence) {
        return Map.of(
                "documentId", query.documentId(),
                "sessionId", query.sessionId(),
                "noEvidence", noEvidence,
                "citationCount", retrieval.citations().size()
        );
    }

    protected void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException ex) {
            throw new IllegalStateException("sse_send_failed");
        }
    }

    private record ResolvedQaQuery(
            Long userId,
            Long documentId,
            String question,
            Integer topK,
            Integer indexVersion,
            String sessionId
    ) {
    }
}
