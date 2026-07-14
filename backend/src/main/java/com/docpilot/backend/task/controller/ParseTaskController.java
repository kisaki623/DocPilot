package com.docpilot.backend.task.controller;

import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.task.dto.ParseTaskCreateRequest;
import com.docpilot.backend.task.service.ParseTaskService;
import com.docpilot.backend.task.vo.ParseTaskCreateResponse;
import com.docpilot.backend.task.vo.ParseTaskStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task")
public class ParseTaskController {

    private final ParseTaskService parseTaskService;

    public ParseTaskController(ParseTaskService parseTaskService) {
        this.parseTaskService = parseTaskService;
    }

    @PostMapping("/parse/create")
    public ApiResponse<ParseTaskCreateResponse> create(@RequestBody ParseTaskCreateRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(parseTaskService.create(request.getDocumentId(), userId));
    }

    @PostMapping("/parse/retry")
    public ApiResponse<ParseTaskCreateResponse> retry(@RequestBody ParseTaskCreateRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(parseTaskService.retry(request.getDocumentId(), userId));
    }

    @PostMapping("/parse/reparse")
    public ApiResponse<ParseTaskCreateResponse> reparse(@RequestBody ParseTaskCreateRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(parseTaskService.reparse(request.getDocumentId(), userId));
    }

    @GetMapping("/parse/status")
    public ApiResponse<ParseTaskStatusResponse> status(@RequestParam Long documentId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(parseTaskService.status(documentId, userId));
    }
}

