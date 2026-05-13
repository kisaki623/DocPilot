package com.docpilot.backend.ai.agent.service.impl;

import com.docpilot.backend.ai.agent.dto.DocumentAgentRequest;
import com.docpilot.backend.ai.agent.entity.AgentTask;
import com.docpilot.backend.ai.agent.service.DocumentAgentService;
import com.docpilot.backend.ai.agent.service.AgentTaskPersistenceService;
import com.docpilot.backend.ai.agent.tool.DocumentQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentStatusTool;
import com.docpilot.backend.ai.agent.tool.DocumentSummaryTool;
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

    public DocumentAgentServiceImpl(ToolRegistry toolRegistry,
                                    ToolSelector toolSelector,
                                    AgentTaskPersistenceService persistenceService) {
        this.toolRegistry = toolRegistry;
        this.toolSelector = toolSelector;
        this.persistenceService = persistenceService;
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
                response.setRoutingReason("\u6587\u6863\u5c1a\u672a\u89e3\u6790\u5b8c\u6210\uff0c\u8def\u7531\u5230\u72b6\u6001\u63d0\u793a\uff0c\u907f\u514d\u6267\u884c\u6458\u8981\u6216\u95ee\u7b54\u5de5\u5177");
                response.setMatchedKeywords(List.of());
                response.setFinalAnswer(buildPendingAnswer(detail));
                response.setSteps(steps);
                return completeSuccess(taskId, response, beginNanos);
            }

            ToolSelector.SelectResult selection = toolSelector.select(task);
            response.setRoutingReason(selection.reason());
            response.setMatchedKeywords(selection.matchedKeywords());

            if ("status_only".equals(selection.decision())) {
                response.setDecision("status_only");
                response.setFinalAnswer(buildStatusAnswer(detail));
                response.setSteps(steps);
                return completeSuccess(taskId, response, beginNanos);
            }

            if ("summary_tool".equals(selection.decision())) {
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
