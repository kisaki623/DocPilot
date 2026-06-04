package com.docpilot.backend.ai.agent.tool.openai;

public interface OpenAiFunctionCallingService {

    OpenAiFunctionCallingResult callTools(Long currentUserId, String userMessage, String mockModelResponseJson);
}
