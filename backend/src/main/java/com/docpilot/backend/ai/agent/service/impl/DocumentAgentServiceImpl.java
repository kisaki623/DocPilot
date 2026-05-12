package com.docpilot.backend.ai.agent.service.impl;

import com.docpilot.backend.ai.agent.dto.DocumentAgentRequest;
import com.docpilot.backend.ai.agent.service.DocumentAgentService;
import com.docpilot.backend.ai.agent.tool.DocumentQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentStatusTool;
import com.docpilot.backend.ai.agent.tool.DocumentSummaryTool;
import com.docpilot.backend.ai.agent.vo.DocumentAgentResponse;
import com.docpilot.backend.ai.vo.DocumentQaResponse;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentAgentServiceImpl implements DocumentAgentService {

    private static final int STEP_OUTPUT_MAX_LENGTH = 200;

    private static final List<String> STATUS_KEYWORDS = List.of(
            "status", "progress", "state", "解析状态", "状态", "进度", "是否完成"
    );
    private static final List<String> SUMMARY_KEYWORDS = List.of(
            "summary", "summarize", "overview", "brief", "摘要", "总结", "概览"
    );
    private static final List<String> EVIDENCE_KEYWORDS = List.of(
            "evidence", "citation", "cite", "proof", "依据", "引用", "出处", "证据"
    );

    private final DocumentStatusTool documentStatusTool;
    private final DocumentSummaryTool documentSummaryTool;
    private final DocumentQaTool documentQaTool;

    public DocumentAgentServiceImpl(DocumentStatusTool documentStatusTool,
                                    DocumentSummaryTool documentSummaryTool,
                                    DocumentQaTool documentQaTool) {
        this.documentStatusTool = documentStatusTool;
        this.documentSummaryTool = documentSummaryTool;
        this.documentQaTool = documentQaTool;
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

        DocumentStatusTool.StatusResult detail = statusResult.value();
        if (!detail.parseReady()) {
            response.setDecision("status_only");
            response.setFinalAnswer(buildPendingAnswer(detail));
            response.setSteps(steps);
            return finalizeResponse(response, beginNanos);
        }

        boolean statusIntent = containsAnyKeyword(task, STATUS_KEYWORDS);
        boolean summaryIntent = containsAnyKeyword(task, SUMMARY_KEYWORDS);
        boolean evidenceIntent = containsAnyKeyword(task, EVIDENCE_KEYWORDS);

        if (statusIntent && !summaryIntent && !evidenceIntent) {
            response.setDecision("status_only");
            response.setFinalAnswer(buildStatusAnswer(detail));
            response.setSteps(steps);
            return finalizeResponse(response, beginNanos);
        }

        if (summaryIntent && !evidenceIntent) {
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

            response.setDecision("summary_tool");
            response.setFinalAnswer(summaryResult.value().output());
            response.setSteps(steps);
            return finalizeResponse(response, beginNanos);
        }

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

        response.setDecision("qa_tool");
        response.setFinalAnswer(qa.getAnswer());
        response.setSessionId(qa.getSessionId());
        response.setCitations(qa.getCitations());
        response.setSteps(steps);
        return finalizeResponse(response, beginNanos);
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

    private boolean containsAnyKeyword(String task, List<String> keywords) {
        String normalized = safeText(task).toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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
