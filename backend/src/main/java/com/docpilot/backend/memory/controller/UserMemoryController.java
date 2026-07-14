package com.docpilot.backend.memory.controller;

import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.memory.dto.MemorySuggestionExtractRequest;
import com.docpilot.backend.memory.dto.MemorySuggestionResolveRequest;
import com.docpilot.backend.memory.dto.UserMemoryCreateRequest;
import com.docpilot.backend.memory.dto.UserMemoryUpdateRequest;
import com.docpilot.backend.memory.service.UserMemoryService;
import com.docpilot.backend.memory.vo.UserMemoryResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/memories")
public class UserMemoryController {

    private final UserMemoryService userMemoryService;

    public UserMemoryController(UserMemoryService userMemoryService) {
        this.userMemoryService = userMemoryService;
    }

    @PostMapping
    public ApiResponse<UserMemoryResponse> create(@RequestBody UserMemoryCreateRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(userMemoryService.create(
                userId,
                request == null ? null : request.getMemoryType(),
                request == null ? null : request.getContent(),
                request == null ? null : request.getPriority(),
                request == null ? null : request.getSourceConversationId(),
                request == null ? null : request.getSourceMessageId()
        ));
    }

    @GetMapping
    public ApiResponse<List<UserMemoryResponse>> list(@RequestParam(value = "memoryType", required = false) String memoryType,
                                                      @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(userMemoryService.list(userId, memoryType, limit));
    }

    @GetMapping("/disabled")
    public ApiResponse<List<UserMemoryResponse>> listDisabled(
            @RequestParam(value = "memoryType", required = false) String memoryType,
            @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(userMemoryService.listDisabled(userId, memoryType, limit));
    }

    @GetMapping("/suggestions")
    public ApiResponse<List<UserMemoryResponse>> listSuggestions(
            @RequestParam(value = "memoryType", required = false) String memoryType,
            @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(userMemoryService.listSuggestions(userId, memoryType, limit));
    }

    @PostMapping("/suggestions/extract")
    public ApiResponse<List<UserMemoryResponse>> extractSuggestions(@RequestBody MemorySuggestionExtractRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(userMemoryService.extractSuggestions(
                userId,
                request == null ? null : request.getConversationId(),
                request == null ? null : request.getLimit()
        ));
    }

    @PostMapping("/suggestions/{memoryId}/accept")
    public ApiResponse<UserMemoryResponse> acceptSuggestion(@PathVariable("memoryId") Long memoryId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(userMemoryService.acceptSuggestion(userId, memoryId));
    }

    @PostMapping("/suggestions/{memoryId}/ignore")
    public ApiResponse<UserMemoryResponse> ignoreSuggestion(@PathVariable("memoryId") Long memoryId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(userMemoryService.ignoreSuggestion(userId, memoryId));
    }

    @PostMapping("/suggestions/{memoryId}/resolve")
    public ApiResponse<UserMemoryResponse> resolveSuggestion(@PathVariable("memoryId") Long memoryId,
                                                             @RequestBody MemorySuggestionResolveRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(userMemoryService.resolveSuggestion(
                userId,
                memoryId,
                request == null ? null : request.getAction(),
                request == null ? null : request.getActiveMemoryId(),
                request == null ? null : request.getMergedContent(),
                request == null ? null : request.getPriority()
        ));
    }

    @PatchMapping("/{memoryId}")
    public ApiResponse<UserMemoryResponse> update(@PathVariable("memoryId") Long memoryId,
                                                  @RequestBody UserMemoryUpdateRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(userMemoryService.update(
                userId,
                memoryId,
                request == null ? null : request.getContent(),
                request == null ? null : request.getPriority()
        ));
    }

    @PostMapping("/{memoryId}/disable")
    public ApiResponse<UserMemoryResponse> disable(@PathVariable("memoryId") Long memoryId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(userMemoryService.disable(userId, memoryId));
    }

    @PostMapping("/{memoryId}/restore")
    public ApiResponse<UserMemoryResponse> restore(@PathVariable("memoryId") Long memoryId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(userMemoryService.restore(userId, memoryId));
    }

    @DeleteMapping("/{memoryId}")
    public ApiResponse<UserMemoryResponse> delete(@PathVariable("memoryId") Long memoryId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(userMemoryService.delete(userId, memoryId));
    }
}
