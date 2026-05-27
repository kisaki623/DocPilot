package com.docpilot.backend.ai.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FakeLlmToolSelector implements LlmToolSelector {

    private final DocumentToolSelector documentToolSelector;

    public FakeLlmToolSelector(DocumentToolSelector documentToolSelector) {
        this.documentToolSelector = documentToolSelector;
    }

    @Override
    public LlmToolSelectionResult selectWithPrompt(String task,
                                                   boolean parseReady,
                                                   boolean hasSummary,
                                                   List<ToolDefinition> toolDefinitions) {
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            throw new IllegalArgumentException("toolDefinitions must not be empty");
        }
        if (!parseReady) {
            return new LlmToolSelectionResult(
                    "status_only",
                    List.of("document_status_tool"),
                    "Document is not parse-ready, so the shadow selector would only check document status.",
                    List.of("parseReady=false"),
                    0.68d
            );
        }

        ToolSelector.SelectResult primaryLikeResult = documentToolSelector.select(task);
        return new LlmToolSelectionResult(
                primaryLikeResult.decision(),
                primaryLikeResult.toolNames(),
                "Fake shadow selector mirrors keyword selector: " + primaryLikeResult.reason(),
                primaryLikeResult.matchedKeywords(),
                hasSummary ? 0.86d : 0.82d
        );
    }
}
