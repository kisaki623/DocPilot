package com.docpilot.backend.ai.agent.tool;

import java.util.List;

public interface ToolSelector {

    SelectResult select(String task);

    record SelectResult(String decision, List<String> toolNames) {
        public SelectResult {
            toolNames = List.copyOf(toolNames);
        }
    }
}
