package com.docpilot.backend.ai.agent.service.impl;

import com.docpilot.backend.ai.agent.entity.AgentStep;
import com.docpilot.backend.ai.agent.entity.AgentTask;
import com.docpilot.backend.ai.agent.mapper.AgentStepMapper;
import com.docpilot.backend.ai.agent.mapper.AgentTaskMapper;
import com.docpilot.backend.ai.agent.service.AgentTaskPersistenceService;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentTaskPersistenceServiceImpl implements AgentTaskPersistenceService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private static final int TASK_INPUT_MAX_LENGTH = 1000;
    private static final int DECISION_MAX_LENGTH = 255;
    private static final int FINAL_ANSWER_MAX_LENGTH = 2000;
    private static final int ERROR_MSG_MAX_LENGTH = 512;
    private static final int TOOL_NAME_MAX_LENGTH = 100;
    private static final int STEP_SUMMARY_MAX_LENGTH = 500;

    private final AgentTaskMapper agentTaskMapper;
    private final AgentStepMapper agentStepMapper;

    public AgentTaskPersistenceServiceImpl(AgentTaskMapper agentTaskMapper,
                                           AgentStepMapper agentStepMapper) {
        this.agentTaskMapper = agentTaskMapper;
        this.agentStepMapper = agentStepMapper;
    }

    @Override
    public AgentTask createTask(Long userId, Long documentId, String taskInput, String sessionId) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(documentId, "documentId");
        ValidationUtils.requireNonBlank(taskInput, "taskInput");

        LocalDateTime now = LocalDateTime.now();
        AgentTask task = new AgentTask();
        task.setUserId(userId);
        task.setDocumentId(documentId);
        task.setSessionId(truncate(sessionId, 128));
        task.setTaskInput(truncate(taskInput, TASK_INPUT_MAX_LENGTH));
        task.setStatus(STATUS_RUNNING);
        task.setStartTime(now);

        agentTaskMapper.insert(task);
        return task;
    }

    @Override
    public void updateTaskSuccess(Long taskId, String decision, String finalAnswer, Long totalDurationMs) {
        AgentTask task = requireTask(taskId);
        task.setStatus(STATUS_SUCCESS);
        task.setDecision(truncate(decision, DECISION_MAX_LENGTH));
        task.setFinalAnswer(truncate(finalAnswer, FINAL_ANSWER_MAX_LENGTH));
        task.setTotalDurationMs(totalDurationMs);
        task.setErrorMsg(null);
        task.setFinishTime(LocalDateTime.now());
        agentTaskMapper.updateById(task);
    }

    @Override
    public void updateTaskFailed(Long taskId, String errorMsg) {
        AgentTask task = requireTask(taskId);
        task.setStatus(STATUS_FAILED);
        task.setErrorMsg(truncate(errorMsg, ERROR_MSG_MAX_LENGTH));
        task.setFinishTime(LocalDateTime.now());
        agentTaskMapper.updateById(task);
    }

    @Override
    public AgentStep createStep(Long taskId,
                                int stepIndex,
                                String toolName,
                                String inputSummary,
                                String outputSummary,
                                Long durationMs,
                                String status) {
        return createStep(taskId, stepIndex, toolName, inputSummary, outputSummary, durationMs, status, null);
    }

    @Override
    public AgentStep createStep(Long taskId,
                                int stepIndex,
                                String toolName,
                                String inputSummary,
                                String outputSummary,
                                Long durationMs,
                                String status,
                                String errorMsg) {
        ValidationUtils.requireNonNull(taskId, "taskId");
        ValidationUtils.requireNonBlank(toolName, "toolName");
        ValidationUtils.requireNonBlank(status, "status");

        LocalDateTime now = LocalDateTime.now();
        AgentStep step = new AgentStep();
        step.setTaskId(taskId);
        step.setStepIndex(stepIndex);
        step.setToolName(truncate(toolName, TOOL_NAME_MAX_LENGTH));
        step.setInputSummary(truncate(inputSummary, STEP_SUMMARY_MAX_LENGTH));
        step.setOutputSummary(truncate(outputSummary, STEP_SUMMARY_MAX_LENGTH));
        step.setStatus(status);
        step.setErrorMsg(truncate(errorMsg, ERROR_MSG_MAX_LENGTH));
        step.setDurationMs(durationMs);
        step.setStartTime(now);
        step.setFinishTime(now);

        agentStepMapper.insert(step);
        return step;
    }

    @Override
    public AgentTask getTaskByUserAndId(Long userId, Long taskId) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(taskId, "taskId");
        AgentTask task = agentTaskMapper.selectByUserAndId(userId, taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "agent task not found");
        }
        return task;
    }

    @Override
    public List<AgentStep> getStepsByTaskId(Long taskId) {
        ValidationUtils.requireNonNull(taskId, "taskId");
        return agentStepMapper.selectByTaskId(taskId);
    }

    private AgentTask requireTask(Long taskId) {
        ValidationUtils.requireNonNull(taskId, "taskId");
        AgentTask task = agentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("agent task not found, taskId=" + taskId);
        }
        return task;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
