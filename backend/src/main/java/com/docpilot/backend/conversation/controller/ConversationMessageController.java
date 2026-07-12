package com.docpilot.backend.conversation.controller;

import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.ai.context.ContextTrace;
import com.docpilot.backend.conversation.dto.ConversationMessageSendRequest;
import com.docpilot.backend.conversation.service.ConversationMessageService;
import com.docpilot.backend.conversation.vo.ConversationMessageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class ConversationMessageController {

    private final ConversationMessageService conversationMessageService;

    public ConversationMessageController(ConversationMessageService conversationMessageService) {
        this.conversationMessageService = conversationMessageService;
    }

    @PostMapping
    public ApiResponse<ConversationMessageResponse> send(@PathVariable("conversationId") Long conversationId,
                                                         @RequestBody ConversationMessageSendRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(conversationMessageService.send(
                userId,
                conversationId,
                request == null ? null : request.getContent(),
                request == null ? null : request.getGroundingPolicy()
        ));
    }

    @GetMapping
    public ApiResponse<List<ConversationMessageResponse>> list(@PathVariable("conversationId") Long conversationId,
                                                               @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(conversationMessageService.list(userId, conversationId, limit));
    }

    @GetMapping("/{messageId}/trace")
    public ApiResponse<ContextTrace> trace(@PathVariable("conversationId") Long conversationId,
                                           @PathVariable("messageId") Long messageId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(conversationMessageService.getTrace(userId, conversationId, messageId));
    }
}
