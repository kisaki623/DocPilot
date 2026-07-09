package com.docpilot.backend.ai.agent.service.impl;

import com.docpilot.backend.ai.agent.dto.KnowledgeBaseAgentRequest;
import com.docpilot.backend.ai.agent.dto.ToolCallRequest;
import com.docpilot.backend.ai.agent.service.KnowledgeBaseAgentService;
import com.docpilot.backend.ai.agent.service.ToolCallService;
import com.docpilot.backend.ai.agent.tool.KnowledgeBaseSearchTool;
import com.docpilot.backend.ai.agent.tool.ToolSelector;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallStatus;
import com.docpilot.backend.ai.agent.vo.KnowledgeBaseAgentResponse;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaAnswer;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.service.KnowledgeBaseRagQaService;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeBaseAgentServiceImpl implements KnowledgeBaseAgentService {

    private static final int SUMMARY_MAX_LENGTH = 180;
    private static final String KB_RAG_QA_STEP_NAME = "knowledge_base_rag_qa";
    private static final String UNSUPPORTED_INTENT_ANSWER =
            "KB Agent 目前支持检索证据和基于知识库证据回答；该意图暂未开放。";
    private static final String SEARCH_TOOL_UNAVAILABLE_ANSWER =
            "知识库检索工具暂不可用，请稍后重试。";

    private final ToolCallService toolCallService;
    private final ToolSelector toolSelector;
    private final KnowledgeBaseRagQaService knowledgeBaseRagQaService;

    public KnowledgeBaseAgentServiceImpl(ToolCallService toolCallService,
                                         ToolSelector toolSelector,
                                         KnowledgeBaseRagQaService knowledgeBaseRagQaService) {
        this.toolCallService = toolCallService;
        this.toolSelector = toolSelector;
        this.knowledgeBaseRagQaService = knowledgeBaseRagQaService;
    }

    @Override
    public KnowledgeBaseAgentResponse run(Long userId, Long knowledgeBaseId, KnowledgeBaseAgentRequest request) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(knowledgeBaseId, "knowledgeBaseId");
        if (knowledgeBaseId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "knowledgeBaseId must be positive");
        }
        String task = request == null ? "" : safeText(request.getTask()).trim();
        if (task.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "task must not be blank");
        }

        long beginNanos = System.nanoTime();
        KnowledgeBaseAgentResponse response = new KnowledgeBaseAgentResponse();
        response.setTraceId("kb-agent-" + UUID.randomUUID());
        response.setStartedAt(Instant.now().toString());
        response.setKnowledgeBaseId(knowledgeBaseId);
        response.setTask(task);

        ToolSelector.SelectResult selection = toolSelector.select(task);
        response.setDecision(selection.decision());
        response.setRoutingReason(selection.reason());
        response.setMatchedKeywords(selection.matchedKeywords());

        if (isAnswerDecision(selection.decision())) {
            return runAnswer(userId, knowledgeBaseId, request, task, response, beginNanos);
        }

        if (!"search_tool".equals(selection.decision())) {
            response.setSuccess(false);
            response.setFinalAnswer(UNSUPPORTED_INTENT_ANSWER);
            response.setSteps(List.of());
            return finalizeResponse(response, beginNanos);
        }

        String inputSummary = "knowledgeBaseId=" + knowledgeBaseId
                + ", topK=" + safeNumber(request.getTopK())
                + ", indexVersion=" + safeNumber(request.getIndexVersion())
                + ", multiQueryEnabled=" + safeText(request.getMultiQueryEnabled())
                + ", maxQueryVariants=" + safeNumber(request.getMaxQueryVariants());
        ToolCallResult searchCall = toolCallService.call(userId, toolCallRequest(
                KnowledgeBaseSearchTool.TOOL_NAME,
                searchArguments(knowledgeBaseId, request, task)
        ));
        response.setSteps(List.of(buildStepFromToolCall(1, inputSummary, searchCall)));

        if (!searchCall.success()) {
            rethrowProtectedToolFailure(searchCall);
            response.setSuccess(false);
            response.setFinalAnswer(SEARCH_TOOL_UNAVAILABLE_ANSWER);
            return finalizeResponse(response, beginNanos);
        }

        KnowledgeBaseSearchTool.SearchResult search = requireToolResult(searchCall, KnowledgeBaseSearchTool.SearchResult.class);
        response.setSuccess(true);
        response.setFinalAnswer(buildSearchAnswer(search));
        response.setDocumentHitCounts(search.documentHitCounts());
        response.setRetrievalMode(search.retrievalMode());
        response.setRerankApplied(search.rerankApplied());
        response.setMultiQueryApplied(search.multiQueryApplied());
        response.setQueryVariantCount(search.queryVariantCount());
        response.setQueryDedupeCount(search.queryDedupeCount());
        response.setRetrievalHits(search.hits());
        response.setCitations(search.citations());
        return finalizeResponse(response, beginNanos);
    }

    private KnowledgeBaseAgentResponse runAnswer(Long userId,
                                                 Long knowledgeBaseId,
                                                 KnowledgeBaseAgentRequest request,
                                                 String task,
                                                 KnowledgeBaseAgentResponse response,
                                                 long beginNanos) {
        long stepBeginNanos = System.nanoTime();
        KnowledgeBaseRagQaAnswer answer = knowledgeBaseRagQaService.answer(new KnowledgeBaseRagQaQuery(
                userId,
                knowledgeBaseId,
                task,
                request.getTopK(),
                request.getIndexVersion(),
                request.getSessionId(),
                request.getMultiQueryEnabled(),
                request.getMaxQueryVariants()
        ));
        long stepDurationMs = (System.nanoTime() - stepBeginNanos) / 1_000_000L;
        response.setSuccess(!answer.noEvidence());
        response.setFinalAnswer(answer.answer());
        response.setNoEvidence(answer.noEvidence());
        response.setFallbackUsed(answer.fallbackUsed());
        response.setFallbackReason(answer.fallbackReason());
        response.setAnswerProvider(answer.answerProvider());
        response.setAnswerModel(answer.answerModel());
        response.setModelCallCount(answer.modelCallCount());
        applyRetrieval(response, answer.retrieval());
        response.setSteps(List.of(buildStepFromQa(1, knowledgeBaseId, request, answer, stepDurationMs)));
        return finalizeResponse(response, beginNanos);
    }

    private boolean isAnswerDecision(String decision) {
        return "rag_tool".equals(decision) || "qa_tool".equals(decision) || "summary_tool".equals(decision);
    }

    private ToolCallRequest toolCallRequest(String toolName, Map<String, Object> arguments) {
        ToolCallRequest request = new ToolCallRequest();
        request.setToolName(toolName);
        request.setArguments(arguments);
        return request;
    }

    private Map<String, Object> searchArguments(Long knowledgeBaseId, KnowledgeBaseAgentRequest request, String task) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("knowledgeBaseId", knowledgeBaseId);
        arguments.put("query", task);
        if (request.getTopK() != null) {
            arguments.put("topK", request.getTopK());
        }
        if (request.getIndexVersion() != null) {
            arguments.put("indexVersion", request.getIndexVersion());
        }
        if (request.getMultiQueryEnabled() != null) {
            arguments.put("multiQueryEnabled", request.getMultiQueryEnabled());
        }
        if (request.getMaxQueryVariants() != null) {
            arguments.put("maxQueryVariants", request.getMaxQueryVariants());
        }
        return arguments;
    }

    private void applyRetrieval(KnowledgeBaseAgentResponse response, KnowledgeBaseRagRetrievalResult retrieval) {
        if (retrieval == null) {
            response.setDocumentHitCounts(Map.of());
            response.setRetrievalHits(List.of());
            response.setCitations(List.of());
            return;
        }
        response.setDocumentHitCounts(retrieval.documentHitCounts());
        response.setRetrievalMode(retrieval.retrievalMode());
        response.setRerankApplied(Boolean.TRUE.equals(retrieval.rerankApplied()));
        response.setMultiQueryApplied(Boolean.TRUE.equals(retrieval.multiQueryApplied()));
        response.setQueryVariantCount(retrieval.queryVariantCount());
        response.setQueryDedupeCount(retrieval.queryDedupeCount());
        response.setRetrievalHits(retrieval.hits().stream().map(this::toSearchHit).toList());
        response.setCitations(retrieval.citations().stream().map(this::toSearchCitation).toList());
    }

    private KnowledgeBaseSearchTool.SearchHit toSearchHit(KnowledgeBaseRagRetrievalHit hit) {
        return new KnowledgeBaseSearchTool.SearchHit(
                hit.citationIndex(),
                hit.score(),
                hit.documentId(),
                hit.documentTitle(),
                hit.chunkId(),
                hit.chunkIndex(),
                hit.quoteText(),
                hit.snippet(),
                hit.contentHash(),
                hit.vectorScore(),
                hit.keywordScore(),
                hit.fusedScore(),
                hit.rerankScore()
        );
    }

    private KnowledgeBaseSearchTool.SearchCitation toSearchCitation(KnowledgeBaseRagEvidenceCitation citation) {
        return new KnowledgeBaseSearchTool.SearchCitation(
                citation.index(),
                citation.knowledgeBaseId(),
                citation.documentId(),
                citation.documentTitle(),
                citation.indexVersion(),
                citation.chunkId(),
                citation.chunkIndex(),
                citation.quoteText(),
                citation.snippet(),
                citation.contentHash(),
                citation.score(),
                citation.vectorScore(),
                citation.keywordScore(),
                citation.fusedScore(),
                citation.rerankScore()
        );
    }

    private KnowledgeBaseAgentResponse.AgentStep buildStepFromToolCall(int stepIndex,
                                                                       String inputSummary,
                                                                       ToolCallResult result) {
        String status = ToolCallStatus.SUCCESS.equals(result.status()) ? "success" : "failed";
        String outputSummary = result.success()
                ? result.outputSummary()
                : "tool call failed: " + safeToolCallError(result);
        String errorMessage = result.success() ? "" : safeToolCallError(result);
        KnowledgeBaseAgentResponse.AgentStep step = new KnowledgeBaseAgentResponse.AgentStep();
        step.setStepIndex(stepIndex);
        step.setToolName(result.toolName());
        step.setInputSummary(summarize(inputSummary));
        step.setOutputSummary(summarize(outputSummary));
        step.setErrorMessage(summarize(errorMessage));
        step.setDurationMs(result.durationMs());
        step.setStatus(status);
        return step;
    }

    private KnowledgeBaseAgentResponse.AgentStep buildStepFromQa(int stepIndex,
                                                                 Long knowledgeBaseId,
                                                                 KnowledgeBaseAgentRequest request,
                                                                 KnowledgeBaseRagQaAnswer answer,
                                                                 long durationMs) {
        KnowledgeBaseRagRetrievalResult retrieval = answer.retrieval();
        int hitCount = retrieval == null ? 0 : retrieval.hits().size();
        int citationCount = retrieval == null ? 0 : retrieval.citations().size();
        int documentCount = retrieval == null ? 0 : retrieval.documentIds().size();
        String inputSummary = "knowledgeBaseId=" + knowledgeBaseId
                + ", topK=" + safeNumber(request.getTopK())
                + ", indexVersion=" + safeNumber(request.getIndexVersion())
                + ", sessionIdPresent=" + !safeText(request.getSessionId()).isBlank()
                + ", multiQueryEnabled=" + safeText(request.getMultiQueryEnabled())
                + ", maxQueryVariants=" + safeNumber(request.getMaxQueryVariants());
        String outputSummary = "hitCount=" + hitCount
                + ", citationCount=" + citationCount
                + ", documentCount=" + documentCount
                + ", noEvidence=" + answer.noEvidence()
                + ", fallbackUsed=" + answer.fallbackUsed()
                + ", modelCallCount=" + answer.modelCallCount();
        KnowledgeBaseAgentResponse.AgentStep step = new KnowledgeBaseAgentResponse.AgentStep();
        step.setStepIndex(stepIndex);
        step.setToolName(KB_RAG_QA_STEP_NAME);
        step.setInputSummary(summarize(inputSummary));
        step.setOutputSummary(summarize(outputSummary));
        step.setErrorMessage("");
        step.setDurationMs(durationMs);
        step.setStatus(answer.noEvidence() ? "review" : "success");
        return step;
    }

    private void rethrowProtectedToolFailure(ToolCallResult result) {
        ErrorCode errorCode = errorCodeFrom(result.errorType());
        if (errorCode == ErrorCode.KNOWLEDGE_BASE_FORBIDDEN
                || errorCode == ErrorCode.KNOWLEDGE_BASE_NOT_FOUND
                || errorCode == ErrorCode.UNAUTHORIZED
                || errorCode == ErrorCode.FORBIDDEN) {
            throw new BusinessException(errorCode);
        }
    }

    private ErrorCode errorCodeFrom(String errorType) {
        if (errorType == null || errorType.isBlank()) {
            return null;
        }
        try {
            return ErrorCode.valueOf(errorType.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String safeToolCallError(ToolCallResult result) {
        if (result == null || result.errorType() == null || result.errorType().isBlank()) {
            return "TOOL_CALL_FAILED";
        }
        return result.errorType();
    }

    private <T> T requireToolResult(ToolCallResult result, Class<T> expectedType) {
        Object value = result.result();
        if (!expectedType.isInstance(value)) {
            throw new IllegalStateException("Unexpected tool result type for " + result.toolName());
        }
        return expectedType.cast(value);
    }

    private String buildSearchAnswer(KnowledgeBaseSearchTool.SearchResult search) {
        if (search == null || search.noEvidence() || search.hitCount() <= 0) {
            return "未在当前知识库索引中检索到足够证据。";
        }
        StringBuilder answer = new StringBuilder();
        answer.append("检索到 ")
                .append(search.hitCount())
                .append(" 个证据片段，citationCount=")
                .append(search.citationCount())
                .append("，topK=")
                .append(search.topK())
                .append("，retrievalMode=")
                .append(safeText(search.retrievalMode()))
                .append("。");
        if (!search.documentHitCounts().isEmpty()) {
            answer.append(" documentHitCounts=").append(search.documentHitCounts());
        }
        int limit = Math.min(3, search.citations().size());
        for (int i = 0; i < limit; i++) {
            KnowledgeBaseSearchTool.SearchCitation citation = search.citations().get(i);
            answer.append("\n[").append(citation.index()).append("] ")
                    .append("documentId=").append(citation.documentId())
                    .append(", chunkId=").append(citation.chunkId())
                    .append(", chunkIndex=").append(citation.chunkIndex())
                    .append(", score=").append(String.format(java.util.Locale.ROOT, "%.4f", citation.score()));
            if (!safeText(citation.documentTitle()).isBlank()) {
                answer.append(", title=").append(citation.documentTitle());
            }
            if (!safeText(citation.quoteText()).isBlank()) {
                answer.append(", quote=").append(citation.quoteText());
            }
        }
        return answer.toString();
    }

    private KnowledgeBaseAgentResponse finalizeResponse(KnowledgeBaseAgentResponse response, long beginNanos) {
        response.setTotalDurationMs((System.nanoTime() - beginNanos) / 1_000_000L);
        response.setFinishedAt(Instant.now().toString());
        return response;
    }

    private String summarize(String text) {
        String normalized = safeText(text).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= SUMMARY_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, SUMMARY_MAX_LENGTH) + "...";
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeNumber(Number value) {
        return value == null ? "" : String.valueOf(value);
    }
}
