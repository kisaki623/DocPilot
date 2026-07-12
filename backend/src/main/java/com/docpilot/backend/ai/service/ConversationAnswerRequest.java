package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.context.GroundingPolicy;
import com.docpilot.backend.ai.context.PromptMessage;
import com.docpilot.backend.ai.context.RouteDecision;

import java.util.List;

public record ConversationAnswerRequest(
        List<PromptMessage> promptMessages,
        String question,
        GroundingPolicy groundingPolicy,
        RouteDecision routeDecision
) {

    public ConversationAnswerRequest {
        promptMessages = promptMessages == null ? List.of() : List.copyOf(promptMessages);
        question = question == null ? "" : question.trim();
        groundingPolicy = groundingPolicy == null ? GroundingPolicy.MODEL_ONLY : groundingPolicy;
        routeDecision = routeDecision == null ? RouteDecision.MODEL_ONLY : routeDecision;
    }
}
