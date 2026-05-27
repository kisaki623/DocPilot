package com.docpilot.backend.ai.agent.tool;

import java.util.List;

public interface ToolSelector {

    SelectResult select(String task);

    record SelectResult(String decision, List<String> toolNames, String reason, List<String> matchedKeywords) {
        public SelectResult {
            toolNames = List.copyOf(toolNames);
            reason = reason == null ? "" : reason;
            matchedKeywords = matchedKeywords == null ? List.of() : List.copyOf(matchedKeywords);
        }

        public static SelectResult of(String decision, List<String> toolNames) {
            return new SelectResult(decision, toolNames, "", List.of());
        }
    }
}
