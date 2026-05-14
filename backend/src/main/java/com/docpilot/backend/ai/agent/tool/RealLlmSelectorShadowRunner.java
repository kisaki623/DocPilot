package com.docpilot.backend.ai.agent.tool;

import java.util.List;

public class RealLlmSelectorShadowRunner {

    private final RealLlmToolSelector realLlmToolSelector;

    public RealLlmSelectorShadowRunner(RealLlmToolSelector realLlmToolSelector) {
        this.realLlmToolSelector = realLlmToolSelector;
    }

    public RealLlmSelectorShadowRunResult run(String primaryDecision,
                                              String task,
                                              boolean parseReady,
                                              boolean hasSummary,
                                              List<ToolDefinition> toolDefinitions) {
        try {
            LlmToolSelectionResult shadowSelection = realLlmToolSelector.selectWithPrompt(
                    task,
                    parseReady,
                    hasSummary,
                    toolDefinitions
            );
            return RealLlmSelectorShadowRunResult.success(primaryDecision, shadowSelection.decision());
        } catch (Exception ex) {
            return RealLlmSelectorShadowRunResult.failed(primaryDecision, ex.getMessage());
        }
    }
}
