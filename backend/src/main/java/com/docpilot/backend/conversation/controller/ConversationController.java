package com.docpilot.backend.conversation.controller;

import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.conversation.dto.ConversationCreateRequest;
import com.docpilot.backend.conversation.dto.ConversationKnowledgeBaseBindRequest;
import com.docpilot.backend.conversation.service.ConversationService;
import com.docpilot.backend.conversation.vo.ConversationResponse;
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
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ApiResponse<ConversationResponse> create(@RequestBody(required = false) ConversationCreateRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(conversationService.create(
                userId,
                request == null ? null : request.getTitle(),
                request == null ? null : request.getContextMode(),
                request == null ? null : request.getBoundKnowledgeBaseId()
        ));
    }

    @GetMapping
    public ApiResponse<List<ConversationResponse>> list(@RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(conversationService.list(userId, limit));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> detail(@PathVariable("conversationId") Long conversationId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(conversationService.detail(userId, conversationId));
    }

    @PatchMapping("/{conversationId}/knowledge-base")
    public ApiResponse<ConversationResponse> bindKnowledgeBase(@PathVariable("conversationId") Long conversationId,
                                                               @RequestBody ConversationKnowledgeBaseBindRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(conversationService.bindKnowledgeBase(
                userId,
                conversationId,
                request == null ? null : request.getKnowledgeBaseId()
        ));
    }

    @DeleteMapping("/{conversationId}/knowledge-base")
    public ApiResponse<ConversationResponse> unbindKnowledgeBase(@PathVariable("conversationId") Long conversationId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(conversationService.unbindKnowledgeBase(userId, conversationId));
    }
}
