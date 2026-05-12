package com.docpilot.backend.ai.agent.service;

import com.docpilot.backend.ai.agent.entity.AgentStep;
import com.docpilot.backend.ai.agent.entity.AgentTask;

public interface AgentTaskPersistenceService {

    AgentTask createTask(Long userId, Long documentId, String taskInput, String sessionId);

    void updateTaskSuccess(Long taskId, String decision, String finalAnswer, Long totalDurationMs);

    void updateTaskFailed(Long taskId, String errorMsg);

    AgentStep createStep(Long taskId,
                         int stepIndex,
                         String toolName,
                         String inputSummary,
                         String outputSummary,
                         Long durationMs,
                         String status);
}
