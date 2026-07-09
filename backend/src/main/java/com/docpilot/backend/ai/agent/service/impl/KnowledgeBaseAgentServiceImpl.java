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
    private static final String UNSUPPORTED_INTENT_ANSWER =
            "KB Agent P0 目前仅支持检索证据、列出来源、查看相似度或 citation 列表；需要生成答案时请使用知识库 RAG QA。";
    private static final String SEARCH_TOOL_UNAVAILABLE_ANSWER =
            "知识库检索工具暂不可用，请稍后重试。";

    private final ToolCallService toolCallService;
    private final ToolSelector toolSelector;

    public KnowledgeBaseAgentServiceImpl(ToolCallService toolCallService,
                                         ToolSelector toolSelector) {
        this.toolCallService = toolCallService;
        this.toolSelector = toolSelector;
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
                + ", maxQueryVariants=" + safeNumber(request.getMaxQueryVariants())
                + ", query=" + summarize(task);
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
