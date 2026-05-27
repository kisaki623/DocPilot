package com.docpilot.backend.ai.agent.tool;

public interface LlmToolSelectionClient {

    LlmToolSelectionClientResponse completeSelectionPrompt(String prompt);
}
