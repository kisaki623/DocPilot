package com.docpilot.backend.task.controller;

import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.task.dto.ParseTaskCreateRequest;
import com.docpilot.backend.task.service.ParseTaskService;
import com.docpilot.backend.task.vo.ParseTaskCreateResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}

