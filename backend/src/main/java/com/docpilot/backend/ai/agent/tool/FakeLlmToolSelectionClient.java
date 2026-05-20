package com.docpilot.backend.ai.agent.tool;

import java.util.Locale;

public class FakeLlmToolSelectionClient implements LlmToolSelectionClient {

    private static final String DECISION_STATUS = "status_only";
    private static final String DECISION_SUMMARY = "summary_tool";
    private static final String DECISION_QA = "qa_tool";
    private static final String DECISION_RAG = "rag_tool";

    private static final String[] STATUS_KEYWORDS = {
            "status",
            "progress",
            "state",
            "parse status",
            "parsing progress",
            "\u89e3\u6790\u72b6\u6001",
            "\u72b6\u6001",
            "\u8fdb\u5ea6",
            "\u662f\u5426\u5b8c\u6210",
            "\u662f\u5426\u89e3\u6790"
    };

    private static final String[] SUMMARY_KEYWORDS = {
            "summary",
            "summarize",
            "overview",
            "brief",
            "\u6458\u8981",
            "\u603b\u7ed3",
            "\u6982\u89c8"
    };

    private static final String[] EVIDENCE_KEYWORDS = {
            "evidence",
            "citation",
            "cite",
            "proof",
            "source",
            "according to",
            "\u4f9d\u636e",
            "\u5f15\u7528",
            "\u51fa\u5904",
            "\u8bc1\u636e",
            "\u6839\u636e\u539f\u6587",
            "\u539f\u6587\u8bc1\u636e"
    };

    private static final String[] RAG_KEYWORDS = {
            "rag",
            "retrieval",
            "retrieve",
            "topk",
            "top k",
            "similarity",
            "\u68c0\u7d22",
            "\u53ec\u56de",
            "\u76f8\u4f3c\u5ea6",
            "\u7247\u6bb5",
            "\u627e\u4f9d\u636e"
    };

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
        String[] markers = {"Current task:", "Task:"};
        for (String marker : markers) {
            int markerIndex = prompt.indexOf(marker);
            if (markerIndex >= 0) {
                int start = markerIndex + marker.length();
                int end = prompt.indexOf('\n', start);
                if (end < 0) {
                    end = prompt.length();
                }
                return prompt.substring(start, end).trim();
            }
        }
        return prompt.trim();
    }

    private String selectDecision(String normalizedTask) {
        boolean evidenceIntent = containsAny(normalizedTask, EVIDENCE_KEYWORDS);
        boolean summaryIntent = containsAny(normalizedTask, SUMMARY_KEYWORDS);
        boolean statusIntent = containsAny(normalizedTask, STATUS_KEYWORDS);
        boolean ragIntent = containsAny(normalizedTask, RAG_KEYWORDS);

        if (evidenceIntent) {
            return DECISION_QA;
        }
        if (ragIntent) {
            return DECISION_RAG;
        }
        if (summaryIntent) {
            return DECISION_SUMMARY;
        }
        if (statusIntent) {
            return DECISION_STATUS;
        }
        return DECISION_QA;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String buildJson(String decision) {
        return switch (decision) {
            case DECISION_STATUS -> """
                    {
                      "decision": "status_only",
                      "toolNames": ["document_status_tool"],
                      "routingReason": "Fake selector routed to document status for status-related task.",
                      "matchedKeywords": ["status"],
                      "confidence": 0.8
                    }
                    """;
            case DECISION_SUMMARY -> """
                    {
                      "decision": "summary_tool",
                      "toolNames": ["document_status_tool", "document_summary_tool"],
                      "routingReason": "Fake selector routed to summary for summary-related task.",
                      "matchedKeywords": ["summary"],
                      "confidence": 0.8
                    }
                    """;
            case DECISION_RAG -> """
                    {
                      "decision": "rag_tool",
                      "toolNames": ["document_status_tool", "document_rag_tool"],
                      "routingReason": "Fake selector routed to RAG retrieval for retrieval-related task.",
                      "matchedKeywords": ["retrieval"],
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
