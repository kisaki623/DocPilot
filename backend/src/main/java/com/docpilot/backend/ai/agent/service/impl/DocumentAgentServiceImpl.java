package com.docpilot.backend.ai.agent.service.impl;

import com.docpilot.backend.ai.agent.dto.DocumentAgentRequest;
import com.docpilot.backend.ai.agent.entity.AgentTask;
import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;
import com.docpilot.backend.ai.agent.service.DocumentAgentService;
import com.docpilot.backend.ai.agent.service.AgentTaskPersistenceService;
import com.docpilot.backend.ai.agent.tool.DocumentQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagTool;
import com.docpilot.backend.ai.agent.tool.DocumentStatusTool;
import com.docpilot.backend.ai.agent.tool.DocumentSummaryTool;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionParser;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionPromptBuilder;
import com.docpilot.backend.ai.agent.tool.LlmSelectorShadowResult;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionResult;
import com.docpilot.backend.ai.agent.tool.LlmToolSelector;
import com.docpilot.backend.ai.agent.tool.RealLlmSelectorShadowRunResult;
import com.docpilot.backend.ai.agent.tool.RealLlmSelectorShadowRunner;
import com.docpilot.backend.ai.agent.tool.RealLlmToolSelector;
import com.docpilot.backend.ai.agent.tool.RealLlmToolSelectorFactory;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionClientFactory;
import com.docpilot.backend.ai.agent.tool.SelectorMetricsCollector;
import com.docpilot.backend.ai.agent.tool.ToolDefinition;
import com.docpilot.backend.ai.agent.tool.ToolExecutionDecision;
import com.docpilot.backend.ai.agent.tool.ToolDefinitionProvider;
import com.docpilot.backend.ai.agent.tool.ToolRegistry;
import com.docpilot.backend.ai.agent.tool.ToolSelector;
import com.docpilot.backend.ai.agent.vo.DocumentAgentResponse;
import com.docpilot.backend.ai.vo.DocumentQaResponse;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.util.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentAgentServiceImpl implements DocumentAgentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAgentServiceImpl.class);

    private static final int STEP_OUTPUT_MAX_LENGTH = 200;

    private final ToolRegistry toolRegistry;
    private final ToolSelector toolSelector;
    private final AgentTaskPersistenceService persistenceService;
    private final AgentSelectorProperties selectorProperties;
    private final LlmToolSelector shadowToolSelector;
    private final ToolDefinitionProvider toolDefinitionProvider;
    private final SelectorMetricsCollector selectorMetricsCollector;
    private final RealLlmSelectorShadowRunner realShadowRunner;
    private final RealLlmToolSelectorFactory realLlmToolSelectorFactory;

    public DocumentAgentServiceImpl(ToolRegistry toolRegistry,
                                    ToolSelector toolSelector,
                                    AgentTaskPersistenceService persistenceService,
                                    AgentSelectorProperties selectorProperties,
                                    LlmToolSelector shadowToolSelector,
                                    ToolDefinitionProvider toolDefinitionProvider,
                                    SelectorMetricsCollector selectorMetricsCollector,
                                    LlmToolSelectionPromptBuilder realShadowPromptBuilder,
                                    LlmToolSelectionParser realShadowParser) {
        this.toolRegistry = toolRegistry;
        this.toolSelector = toolSelector;
        this.persistenceService = persistenceService;
        this.selectorProperties = selectorProperties;
        this.shadowToolSelector = shadowToolSelector;
        this.toolDefinitionProvider = toolDefinitionProvider;
        this.selectorMetricsCollector = selectorMetricsCollector;
        this.realLlmToolSelectorFactory = new RealLlmToolSelectorFactory(
                new LlmToolSelectionClientFactory(),
                realShadowPromptBuilder,
                realShadowParser
        );
        this.realShadowRunner = new RealLlmSelectorShadowRunner(this.realLlmToolSelectorFactory, selectorProperties);
    }

    @Override
    public DocumentAgentResponse run(Long userId, DocumentAgentRequest request) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(request, "request");
        ValidationUtils.requireNonNull(request.getDocumentId(), "documentId");
        ValidationUtils.requireNonBlank(request.getTask(), "task");

        String task = request.getTask().trim();
        if (task.length() < 2) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "task too short");
        }

        Instant startedAt = Instant.now();
        long beginNanos = System.nanoTime();

        DocumentAgentResponse response = new DocumentAgentResponse();
        response.setTraceId(UUID.randomUUID().toString());
        response.setStartedAt(startedAt.toString());
        response.setDocumentId(request.getDocumentId());
        response.setTask(task);
        response.setSessionId(normalizeSessionId(request.getSessionId()));

        List<DocumentAgentResponse.AgentStep> steps = new ArrayList<>();
        Long taskId = createTaskSafely(userId, request.getDocumentId(), task, response.getSessionId());
        response.setTaskId(taskId);

        try {
            DocumentStatusTool documentStatusTool = toolRegistry.get("document_status_tool");
            TimedResult<DocumentStatusTool.StatusResult> statusResult = timedExecute(() ->
                    documentStatusTool.execute(new DocumentStatusTool.StatusInput(userId, request.getDocumentId())));
            steps.add(buildStep(
                    1,
                    documentStatusTool.getToolName(),
                    "documentId=" + request.getDocumentId(),
                    String.format("parseStatus=%s, parseReady=%s", statusResult.value().parseStatus(), statusResult.value().parseReady()),
                    statusResult.durationMs(),
                    "success"
            ));
            persistLastStep(taskId, steps);

            DocumentStatusTool.StatusResult detail = statusResult.value();
            if (!detail.parseReady()) {
                response.setDecision("status_only");
                response.setPrimaryDecision("status_only");
                response.setLlmDecision("");
                response.setFinalDecision("status_only");
                response.setFallbackUsed(false);
                response.setFallbackReason("");
                response.setExecutionMode(selectorProperties == null ? AgentSelectorProperties.MODE_KEYWORD : selectorProperties.getMode());
                response.setToolSelectionSource(ToolExecutionDecision.SOURCE_KEYWORD);
                response.setRoutingReason("\u6587\u6863\u5c1a\u672a\u89e3\u6790\u5b8c\u6210\uff0c\u8def\u7531\u5230\u72b6\u6001\u63d0\u793a\uff0c\u907f\u514d\u6267\u884c\u6458\u8981\u6216\u95ee\u7b54\u5de5\u5177");
                response.setMatchedKeywords(List.of());
                response.setFinalAnswer(buildPendingAnswer(detail));
                response.setSteps(steps);
                return completeSuccess(taskId, response, beginNanos);
            }

            ToolSelector.SelectResult primarySelection = toolSelector.select(task);
            ToolExecutionDecision executionDecision = selectExecutionDecision(task, detail, primarySelection);
            response.setPrimaryDecision(executionDecision.primaryDecision());
            response.setLlmDecision(executionDecision.llmDecision());
            response.setFinalDecision(executionDecision.finalDecision());
            response.setFallbackUsed(executionDecision.fallbackUsed());
            response.setFallbackReason(executionDecision.fallbackReason());
            response.setExecutionMode(selectorProperties == null ? AgentSelectorProperties.MODE_KEYWORD : selectorProperties.getMode());
            response.setToolSelectionSource(executionDecision.toolSelectionSource());
            response.setRoutingReason(executionDecision.routingReason());
            response.setMatchedKeywords(executionDecision.matchedKeywords());
            compareShadowSelection(task, detail, primarySelection);

            if ("status_only".equals(executionDecision.finalDecision())) {
                response.setDecision("status_only");
                response.setFinalAnswer(buildStatusAnswer(detail));
                response.setSteps(steps);
                return completeSuccess(taskId, response, beginNanos);
            }

            if ("summary_tool".equals(executionDecision.finalDecision())) {
                DocumentSummaryTool documentSummaryTool = toolRegistry.get("document_summary_tool");
                TimedResult<DocumentSummaryTool.SummaryResult> summaryResult = timedExecute(() ->
                        documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(task, detail.summary(), detail.content())));
                steps.add(buildStep(
                        2,
                        documentSummaryTool.getToolName(),
                        "task=" + summarize(task),
                        "source=" + summaryResult.value().source(),
                        summaryResult.durationMs(),
                        "success"
                ));
                persistLastStep(taskId, steps);

                response.setDecision("summary_tool");
                response.setFinalAnswer(summaryResult.value().output());
                response.setSteps(steps);
                return completeSuccess(taskId, response, beginNanos);
            }

            if ("rag_tool".equals(executionDecision.finalDecision())) {
                DocumentRagTool documentRagTool = toolRegistry.get(DocumentRagTool.TOOL_NAME);
                TimedResult<DocumentRagTool.RagResult> ragResult = timedExecute(() ->
                        documentRagTool.execute(new DocumentRagTool.RagInput(request.getDocumentId(), task, detail.content(), 3)));
                DocumentRagTool.RagResult rag = ragResult.value();
                steps.add(buildStep(
                        2,
                        documentRagTool.getToolName(),
                        "documentId=" + request.getDocumentId() + ", topK=" + rag.topK(),
                        String.format("chunks=%d, retrieved=%d", rag.chunkCount(), rag.retrievedChunks().size()),
                        ragResult.durationMs(),
                        "success"
                ));
                persistLastStep(taskId, steps);

                response.setDecision("rag_tool");
                response.setFinalAnswer(buildRagAnswer(rag));
                response.setRagResults(toResponseRagResults(rag.retrievedChunks()));
                response.setRagAnswerContext(rag.answerContext());
                response.setSteps(steps);
                return completeSuccess(taskId, response, beginNanos);
            }

            DocumentQaTool documentQaTool = toolRegistry.get("document_qa_tool");
            TimedResult<DocumentQaResponse> qaResult = timedExecute(() ->
                    documentQaTool.execute(new DocumentQaTool.QaInput(userId, request.getDocumentId(), task, response.getSessionId())));
            DocumentQaResponse qa = qaResult.value();
            steps.add(buildStep(
                    2,
                    documentQaTool.getToolName(),
                    "task=" + summarize(task),
                    String.format("answerLength=%d, citations=%d", safeLength(qa.getAnswer()), safeCitationCount(qa.getCitations())),
                    qaResult.durationMs(),
                    "success"
            ));
            persistLastStep(taskId, steps);

            response.setDecision("qa_tool");
            response.setFinalAnswer(qa.getAnswer());
            response.setSessionId(qa.getSessionId());
            response.setCitations(qa.getCitations());
            response.setSteps(steps);
            return completeSuccess(taskId, response, beginNanos);
        } catch (Exception ex) {
            updateTaskFailedSafely(taskId, ex);
            throw ex;
        }
    }

    private DocumentAgentResponse completeSuccess(Long taskId, DocumentAgentResponse response, long beginNanos) {
        DocumentAgentResponse finalized = finalizeResponse(response, beginNanos);
        updateTaskSuccessSafely(taskId, finalized);
        return finalized;
    }

    private ToolExecutionDecision selectExecutionDecision(String task,
                                                          DocumentStatusTool.StatusResult detail,
                                                          ToolSelector.SelectResult primarySelection) {
        if (selectorProperties == null || !selectorProperties.isLlmExecuteMode()) {
            return ToolExecutionDecision.keyword(primarySelection);
        }
        String provider = selectorProperties.getLlmProvider();
        String llmDecision = "";
        try {
            boolean hasSummary = detail.summary() != null && !detail.summary().isBlank();
            List<ToolDefinition> toolDefinitions = toolDefinitionProvider.getAllDefinitions();
            RealLlmToolSelector realLlmToolSelector = realLlmToolSelectorFactory.create(selectorProperties);
            LlmToolSelectionResult llmSelection = realLlmToolSelector.selectWithPrompt(
                    task,
                    detail.parseReady(),
                    hasSummary,
                    toolDefinitions
            );
            llmDecision = llmSelection.decision();
            validateExecutableLlmSelection(llmSelection);
            log.info("Agent LLM execute selection accepted: provider={}, primaryDecision={}, llmDecision={}",
                    provider, primarySelection.decision(), llmSelection.decision());
            return ToolExecutionDecision.llmExecute(primarySelection, llmSelection, provider);
        } catch (Exception ex) {
            String fallbackReason = buildSafeFallbackReason(ex);
            log.info("Agent LLM execute selection fallback: provider={}, primaryDecision={}, reason={}",
                    provider, primarySelection.decision(), fallbackReason);
            return ToolExecutionDecision.fallback(primarySelection, llmDecision, provider, fallbackReason);
        }
    }

    private void validateExecutableLlmSelection(LlmToolSelectionResult llmSelection) {
        String expectedToolName = expectedToolNameForDecision(llmSelection.decision());
        if (!toolRegistry.getToolNames().contains(expectedToolName)) {
            throw new IllegalArgumentException("Expected tool is not registered for decision");
        }
        if (!llmSelection.toolNames().contains(expectedToolName)) {
            throw new IllegalArgumentException("LLM selection did not include the required registered tool");
        }
        for (String toolName : llmSelection.toolNames()) {
            if (!toolRegistry.getToolNames().contains(toolName)) {
                throw new IllegalArgumentException("LLM selection referenced an unregistered tool");
            }
        }
    }

    private String expectedToolNameForDecision(String decision) {
        return switch (decision) {
            case "status_only" -> "document_status_tool";
            case "summary_tool" -> "document_summary_tool";
            case "rag_tool" -> DocumentRagTool.TOOL_NAME;
            case "qa_tool" -> "document_qa_tool";
            default -> throw new IllegalArgumentException("Unsupported LLM tool decision");
        };
    }

    private String buildSafeFallbackReason(Exception ex) {
        if (ex == null) {
            return "LLM selector failed";
        }
        return "LLM selector failed: " + ex.getClass().getSimpleName();
    }

    private void compareShadowSelection(String task,
                                        DocumentStatusTool.StatusResult detail,
                                        ToolSelector.SelectResult primarySelection) {
        if (selectorProperties == null || !selectorProperties.isShadowEnabled()) {
            return;
        }
        try {
            boolean hasSummary = detail.summary() != null && !detail.summary().isBlank();
            List<ToolDefinition> toolDefinitions = toolDefinitionProvider.getAllDefinitions();
            compareFakeShadowSelection(task, detail, primarySelection, hasSummary, toolDefinitions);
            compareRealShadowSelection(task, primarySelection, hasSummary, toolDefinitions);
        } catch (Exception ex) {
            log.warn("Agent selector shadow preparation failed; primary decision remains active", ex);
        }
    }

    private void compareFakeShadowSelection(String task,
                                            DocumentStatusTool.StatusResult detail,
                                            ToolSelector.SelectResult primarySelection,
                                            boolean hasSummary,
                                            List<ToolDefinition> toolDefinitions) {
        try {
            LlmToolSelectionResult shadowSelection = shadowToolSelector.selectWithPrompt(
                    task,
                    detail.parseReady(),
                    hasSummary,
                    toolDefinitions
            );
            LlmSelectorShadowResult shadowResult = LlmSelectorShadowResult.from(primarySelection, shadowSelection);
            selectorMetricsCollector.record(shadowResult.primaryDecision(), shadowResult.shadowDecision());
            log.info("Agent selector shadow compare: primaryDecision={}, shadowDecision={}, matched={}",
                    shadowResult.primaryDecision(), shadowResult.shadowDecision(), shadowResult.matched());
        } catch (Exception ex) {
            log.warn("Agent selector shadow compare failed; primary decision remains active", ex);
        }
    }

    private void compareRealShadowSelection(String task,
                                            ToolSelector.SelectResult primarySelection,
                                            boolean hasSummary,
                                            List<ToolDefinition> toolDefinitions) {
        if (!selectorProperties.isRealShadowEnabled()) {
            return;
        }
        try {
            RealLlmSelectorShadowRunResult result = realShadowRunner.run(
                    primarySelection.decision(),
                    task,
                    true,
                    hasSummary,
                    toolDefinitions
            );
            if (!result.success()) {
                log.info("Agent real selector shadow skipped: provider={}, primaryDecision={}, error={}",
                        selectorProperties.getLlmProvider(), result.primaryDecision(), result.errorMessage());
                return;
            }
            if (selectorProperties.isRealShadowRecordMetrics() && result.shouldRecordMetrics()) {
                selectorMetricsCollector.record(result.primaryDecision(), result.shadowDecision());
            }
            log.info("Agent real selector shadow compare: provider={}, primaryDecision={}, shadowDecision={}, matched={}, metricsRecorded={}",
                    selectorProperties.getLlmProvider(),
                    result.primaryDecision(),
                    result.shadowDecision(),
                    result.matched(),
                    selectorProperties.isRealShadowRecordMetrics() && result.shouldRecordMetrics());
        } catch (Exception ex) {
            log.warn("Agent real selector shadow failed; primary decision remains active", ex);
        }
    }

    private Long createTaskSafely(Long userId, Long documentId, String task, String sessionId) {
        try {
            AgentTask agentTask = persistenceService.createTask(userId, documentId, task, sessionId);
            return agentTask == null ? null : agentTask.getId();
        } catch (Exception ex) {
            log.warn("Failed to create agent task record, continue without task persistence", ex);
            return null;
        }
    }

    private void updateTaskSuccessSafely(Long taskId, DocumentAgentResponse response) {
        if (taskId == null) {
            return;
        }
        try {
            persistenceService.updateTaskSuccess(taskId, response.getDecision(), response.getFinalAnswer(), response.getTotalDurationMs());
        } catch (Exception ex) {
            log.warn("Failed to mark agent task success, taskId={}", taskId, ex);
        }
    }

    private void updateTaskFailedSafely(Long taskId, Exception failure) {
        if (taskId == null) {
            return;
        }
        try {
            persistenceService.updateTaskFailed(taskId, failure.getMessage());
        } catch (Exception ex) {
            log.warn("Failed to mark agent task failed, taskId={}", taskId, ex);
        }
    }

    private DocumentAgentResponse finalizeResponse(DocumentAgentResponse response, long beginNanos) {
        long durationMs = (System.nanoTime() - beginNanos) / 1_000_000L;
        response.setTotalDurationMs(durationMs);
        response.setFinishedAt(Instant.now().toString());
        response.setSuccess(true);
        return response;
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessionId.trim();
    }

    private String buildPendingAnswer(DocumentStatusTool.StatusResult detail) {
        String desc = detail.parseStatusDescription();
        if (desc == null || desc.isBlank()) {
            desc = "文档解析尚未完成，请稍后重试。";
        }
        return "当前文档状态为 " + detail.parseStatus() + "。"
                + desc
                + " 建议稍后再执行问答或摘要任务。";
    }

    private String buildStatusAnswer(DocumentStatusTool.StatusResult detail) {
        String desc = detail.parseStatusDescription();
        if (desc == null || desc.isBlank()) {
            desc = "文档可继续执行问答或摘要任务。";
        }
        return "文档标题：" + safeText(detail.title())
                + "；解析状态：" + detail.parseStatus()
                + "；状态说明：" + desc;
    }

    private String buildRagAnswer(DocumentRagTool.RagResult rag) {
        if (rag.retrievedChunks().isEmpty()) {
            return "RAG demo did not retrieve chunks. The document may not contain parsed text yet.";
        }
        return "RAG demo retrieved " + rag.retrievedChunks().size()
                + " chunk(s) from " + rag.chunkCount()
                + " indexed chunk(s) using fake embeddings and an in-memory vector store.";
    }

    private List<DocumentAgentResponse.RagRetrievedChunk> toResponseRagResults(List<DocumentRagTool.RetrievedChunk> chunks) {
        List<DocumentAgentResponse.RagRetrievedChunk> results = new ArrayList<>();
        for (DocumentRagTool.RetrievedChunk chunk : chunks) {
            DocumentAgentResponse.RagRetrievedChunk item = new DocumentAgentResponse.RagRetrievedChunk();
            item.setRank(chunk.rank());
            item.setChunkIndex(chunk.chunkIndex());
            item.setScore(chunk.score());
            item.setSnippet(chunk.snippet());
            item.setMetadata(chunk.metadata());
            results.add(item);
        }
        return results;
    }

    private String summarize(String text) {
        String normalized = safeText(text).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= STEP_OUTPUT_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, STEP_OUTPUT_MAX_LENGTH) + "...";
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private int safeCitationCount(List<DocumentQaResponse.CitationItem> citations) {
        return citations == null ? 0 : citations.size();
    }

    private DocumentAgentResponse.AgentStep buildStep(int stepIndex,
                                                      String toolName,
                                                      String inputSummary,
                                                      String outputSummary,
                                                      long durationMs,
                                                      String status) {
        DocumentAgentResponse.AgentStep step = new DocumentAgentResponse.AgentStep();
        step.setStepIndex(stepIndex);
        step.setToolName(toolName);
        step.setInputSummary(summarize(inputSummary));
        step.setOutputSummary(summarize(outputSummary));
        step.setDurationMs(durationMs);
        step.setStatus(status);
        return step;
    }

    private void persistLastStep(Long taskId, List<DocumentAgentResponse.AgentStep> steps) {
        persistStepSafely(taskId, steps.get(steps.size() - 1));
    }

    private void persistStepSafely(Long taskId, DocumentAgentResponse.AgentStep step) {
        if (taskId == null) {
            return;
        }
        try {
            persistenceService.createStep(
                    taskId,
                    step.getStepIndex(),
                    step.getToolName(),
                    step.getInputSummary(),
                    step.getOutputSummary(),
                    step.getDurationMs(),
                    step.getStatus()
            );
        } catch (Exception ex) {
            log.warn("Failed to create agent step record, taskId={}, stepIndex={}", taskId, step.getStepIndex(), ex);
        }
    }

    private <T> TimedResult<T> timedExecute(ThrowingSupplier<T> supplier) {
        long start = System.nanoTime();
        T value = supplier.get();
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        return new TimedResult<>(value, durationMs);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }

    private record TimedResult<T>(T value, long durationMs) {
    }
}
