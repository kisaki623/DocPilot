package com.docpilot.backend.ai.agent.tool;

import java.util.Locale;

public class FakeLlmToolSelectionClient implements LlmToolSelectionClient {

    @Override
    public LlmToolSelectionClientResponse completeSelectionPrompt(String prompt) {
        String task = extractCurrentTask(prompt);
        String normalizedTask = task.toLowerCase(Locale.ROOT);
        String decision = selectDecision(normalizedTask);
        return new LlmToolSelectionClientResponse(
                buildJson(decision),
                "fake",
                "fake-selector",
                false,
                ""
        );
    }

    private String extractCurrentTask(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        String marker = "Current task:";
        int markerIndex = prompt.indexOf(marker);
        if (markerIndex < 0) {
            return prompt.trim();
        }
        int start = markerIndex + marker.length();
        int end = prompt.indexOf('\n', start);
        if (end < 0) {
            end = prompt.length();
        }
        return prompt.substring(start, end).trim();
    }

    private String selectDecision(String normalizedTask) {
        if (containsAny(normalizedTask, "status", "parse status", "解析状态", "状态", "是否解析")) {
            return "status_only";
        }
        if (containsAny(normalizedTask, "summary", "summarize", "overview", "总结", "摘要", "概括")) {
            return "summary_tool";
        }
        return "qa_tool";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String buildJson(String decision) {
        return switch (decision) {
            case "status_only" -> """
                    {
                      "decision": "status_only",
                      "toolNames": ["document_status_tool"],
                      "routingReason": "Fake selector routed to document status for status-related task.",
                      "matchedKeywords": ["status"],
                      "confidence": 0.8
                    }
                    """;
            case "summary_tool" -> """
                    {
                      "decision": "summary_tool",
                      "toolNames": ["document_status_tool", "document_summary_tool"],
                      "routingReason": "Fake selector routed to summary for summary-related task.",
                      "matchedKeywords": ["summary"],
                      "confidence": 0.8
                    }
                    """;
            default -> """
                    {
                      "decision": "qa_tool",
                      "toolNames": ["document_status_tool", "document_qa_tool"],
                      "routingReason": "Fake selector routed to QA for document question task.",
                      "matchedKeywords": ["question"],
                      "confidence": 0.8
                    }
                    """;
        };
    }
}
