package com.docpilot.backend.ai.agent.tool;

import java.util.List;

public interface LlmToolSelector {

    LlmToolSelectionResult selectWithPrompt(String task,
                                            boolean parseReady,
                                            boolean hasSummary,
                                            List<ToolDefinition> toolDefinitions);
}
