package com.docpilot.backend.ai.agent.tool;

import org.springframework.stereotype.Component;

@Component
public class DisabledLlmToolSelectionClient implements LlmToolSelectionClient {

    private static final String DISABLED_MESSAGE = "LLM tool selection client is disabled; no external model call was made.";

    @Override
    public LlmToolSelectionClientResponse completeSelectionPrompt(String prompt) {
        return LlmToolSelectionClientResponse.disabled(DISABLED_MESSAGE);
    }
}
