package com.docpilot.backend.ai.agent.controller;

import com.docpilot.backend.ai.agent.dto.DocumentAgentRequest;
import com.docpilot.backend.ai.agent.entity.AgentStep;
import com.docpilot.backend.ai.agent.entity.AgentTask;
import com.docpilot.backend.ai.agent.service.DocumentAgentService;
import com.docpilot.backend.ai.agent.service.AgentTaskPersistenceService;
import com.docpilot.backend.ai.agent.vo.DocumentAgentResponse;
import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/agent")
public class DocumentAgentController {

    private final DocumentAgentService documentAgentService;
    private final AgentTaskPersistenceService persistenceService;

    public DocumentAgentController(DocumentAgentService documentAgentService,
                                   AgentTaskPersistenceService persistenceService) {
        this.documentAgentService = documentAgentService;
        this.persistenceService = persistenceService;
    }

    @PostMapping("/run")
    public ApiResponse<DocumentAgentResponse> run(@RequestBody DocumentAgentRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(documentAgentService.run(userId, request));
    }

    @GetMapping("/task/{taskId}")
    public ApiResponse<Map<String, Object>> getTask(@PathVariable Long taskId) {
        Long userId = UserHolder.requireUserId();
        AgentTask task = persistenceService.getTaskByUserAndId(userId, taskId);
        List<AgentStep> steps = persistenceService.getStepsByTaskId(taskId);
        return ApiResponse.success(Map.of("task", task, "steps", steps));
    }

    @GetMapping("/task/{taskId}/steps")
    public ApiResponse<List<AgentStep>> getTaskSteps(@PathVariable Long taskId) {
        Long userId = UserHolder.requireUserId();
        persistenceService.getTaskByUserAndId(userId, taskId);
        return ApiResponse.success(persistenceService.getStepsByTaskId(taskId));
    }
}
