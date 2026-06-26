package com.docpilot.backend.conversation.controller;

import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.conversation.service.ConversationSummaryService;
import com.docpilot.backend.conversation.vo.ConversationSummaryResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations/{conversationId}/summary")
public class ConversationSummaryController {

    private final ConversationSummaryService conversationSummaryService;

    public ConversationSummaryController(ConversationSummaryService conversationSummaryService) {
        this.conversationSummaryService = conversationSummaryService;
    }

    @GetMapping
    public ApiResponse<ConversationSummaryResponse> getSummary(@PathVariable("conversationId") Long conversationId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(conversationSummaryService.getSummary(userId, conversationId));
    }

    @PostMapping("/refresh")
    public ApiResponse<ConversationSummaryResponse> refreshSummary(@PathVariable("conversationId") Long conversationId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(conversationSummaryService.refreshSummary(userId, conversationId));
    }

    @DeleteMapping
    public ApiResponse<ConversationSummaryResponse> deleteSummary(@PathVariable("conversationId") Long conversationId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(conversationSummaryService.deleteSummary(userId, conversationId));
    }
}
