package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;

import java.util.List;

public class RealLlmSelectorShadowRunner {

    private final RealLlmToolSelector realLlmToolSelector;
    private final RealLlmToolSelectorFactory realLlmToolSelectorFactory;
    private final AgentSelectorProperties selectorProperties;

    public RealLlmSelectorShadowRunner(RealLlmToolSelector realLlmToolSelector) {
        this.realLlmToolSelector = realLlmToolSelector;
        this.realLlmToolSelectorFactory = null;
        this.selectorProperties = null;
    }

    public RealLlmSelectorShadowRunner(RealLlmToolSelectorFactory realLlmToolSelectorFactory,
                                       AgentSelectorProperties selectorProperties) {
        this.realLlmToolSelector = null;
        this.realLlmToolSelectorFactory = realLlmToolSelectorFactory;
        this.selectorProperties = selectorProperties;
    }

    public RealLlmSelectorShadowRunResult run(String primaryDecision,
                                              String task,
                                              boolean parseReady,
                                              boolean hasSummary,
                                              List<ToolDefinition> toolDefinitions) {
        try {
            LlmToolSelectionResult shadowSelection = resolveSelector().selectWithPrompt(
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

    private RealLlmToolSelector resolveSelector() {
        if (realLlmToolSelector != null) {
            return realLlmToolSelector;
        }
        if (realLlmToolSelectorFactory == null) {
            throw new IllegalStateException("Real LLM tool selector is not configured");
        }
        return realLlmToolSelectorFactory.create(selectorProperties);
    }
}
