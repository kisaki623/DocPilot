package com.docpilot.backend.ai.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class DocumentToolSelector implements ToolSelector {

    private static final List<String> STATUS_KEYWORDS = List.of(
            "status", "progress", "state", "\u89e3\u6790\u72b6\u6001", "\u72b6\u6001", "\u8fdb\u5ea6", "\u662f\u5426\u5b8c\u6210"
    );
    private static final List<String> SUMMARY_KEYWORDS = List.of(
            "summary", "summarize", "overview", "brief", "\u6458\u8981", "\u603b\u7ed3", "\u6982\u89c8"
    );
    private static final List<String> EVIDENCE_KEYWORDS = List.of(
            "evidence", "citation", "cite", "proof", "source", "according to",
            "\u4f9d\u636e", "\u5f15\u7528", "\u51fa\u5904", "\u8bc1\u636e", "\u6839\u636e\u6587\u6863", "\u6839\u636e\u539f\u6587"
    );
    private static final List<String> RAG_KEYWORDS = List.of(
            "rag", "retrieval", "retrieve", "topk", "top k", "similarity",
            "\u68c0\u7d22", "\u53ec\u56de", "\u76f8\u4f3c\u5ea6", "\u7247\u6bb5", "\u627e\u4f9d\u636e"
    );
    private static final List<String> ANSWER_KEYWORDS = List.of(
            "answer", "question", "explain", "why", "what", "which",
            "\u56de\u7b54", "\u95ee\u9898", "\u8bf4\u660e", "\u89e3\u91ca", "\u4e3a\u4ec0\u4e48", "\u5982\u4f55", "\u600e\u4e48", "\u662f\u4ec0\u4e48", "\u591a\u5c11"
    );

    @Override
    public SelectResult select(String task) {
        List<String> statusMatched = matchKeywords(task, STATUS_KEYWORDS);
        List<String> summaryMatched = matchKeywords(task, SUMMARY_KEYWORDS);
        List<String> evidenceMatched = matchKeywords(task, EVIDENCE_KEYWORDS);
        List<String> ragMatched = matchKeywords(task, RAG_KEYWORDS);
        List<String> answerMatched = matchKeywords(task, ANSWER_KEYWORDS);
        boolean statusIntent = !statusMatched.isEmpty();
        boolean summaryIntent = !summaryMatched.isEmpty();
        boolean evidenceIntent = !evidenceMatched.isEmpty();
        boolean ragIntent = !ragMatched.isEmpty();
        boolean answerIntent = !answerMatched.isEmpty();

        if (statusIntent && !summaryIntent && !evidenceIntent && !ragIntent) {
            return new SelectResult(
                    "status_only",
                    List.of("document_status_tool"),
                    "\u547d\u4e2d\u72b6\u6001\u6216\u89e3\u6790\u8fdb\u5ea6\u5173\u952e\u8bcd\uff0c\u4ec5\u9700\u8fd4\u56de\u6587\u6863\u89e3\u6790\u72b6\u6001\uff0c\u56e0\u6b64\u8def\u7531\u5230\u72b6\u6001\u5de5\u5177\u3002",
                    statusMatched
            );
        }
        if (ragIntent || evidenceIntent) {
            java.util.ArrayList<String> matched = new java.util.ArrayList<>();
            matched.addAll(ragMatched);
            matched.addAll(evidenceMatched);
            if (!summaryIntent && !answerIntent) {
                return new SelectResult(
                        "search_tool",
                        List.of("document_status_tool", DocumentSearchTool.TOOL_NAME),
                        "\u547d\u4e2d\u68c0\u7d22\u3001\u53ec\u56de\u3001topK\u3001\u76f8\u4f3c\u5ea6\u6216 evidence \u5c55\u793a\u7c7b\u9700\u6c42\uff0c\u4e14\u672a\u8981\u6c42\u751f\u6210\u7b54\u6848\uff0c\u56e0\u6b64\u8def\u7531\u5230 document search \u5de5\u5177\u3002",
                        List.copyOf(matched)
                );
            }
            return new SelectResult(
                    "rag_tool",
                    List.of("document_status_tool", DocumentRagQaTool.TOOL_NAME),
                    "\u547d\u4e2d RAG\u3001\u68c0\u7d22\u3001\u8bc1\u636e\u6216\u5f15\u7528\u7c7b\u9700\u6c42\uff0c\u9700\u57fa\u4e8e\u65b0 RAG \u7d22\u5f15\u53ec\u56de\u8bc1\u636e\u5e76\u8fd4\u56de citations\uff0c\u56e0\u6b64\u8def\u7531\u5230 RAG QA \u5de5\u5177\u3002",
                    List.copyOf(matched)
            );
        }
        if (summaryIntent && !evidenceIntent) {
            return new SelectResult(
                    "summary_tool",
                    List.of("document_status_tool", "document_summary_tool"),
                    "\u547d\u4e2d\u6458\u8981\u6216\u6982\u62ec\u7c7b\u5173\u952e\u8bcd\uff0c\u9700\u5728\u786e\u8ba4\u6587\u6863\u53ef\u7528\u540e\u8c03\u7528\u6458\u8981\u5de5\u5177\u751f\u6210\u603b\u7ed3\u3002",
                    summaryMatched
            );
        }
        return new SelectResult(
                "qa_tool",
                List.of("document_status_tool", "document_qa_tool"),
                buildQaReason(evidenceIntent),
                collectQaMatchedKeywords(statusMatched, summaryMatched, evidenceMatched)
        );
    }

    private List<String> matchKeywords(String task, List<String> keywords) {
        String normalized = safeText(task).toLowerCase(Locale.ROOT);
        java.util.ArrayList<String> matched = new java.util.ArrayList<>();
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                matched.add(keyword);
            }
        }
        return List.copyOf(matched);
    }

    private List<String> collectQaMatchedKeywords(List<String> statusMatched,
                                                  List<String> summaryMatched,
                                                  List<String> evidenceMatched) {
        if (!evidenceMatched.isEmpty()) {
            return evidenceMatched;
        }
        java.util.ArrayList<String> matched = new java.util.ArrayList<>();
        matched.addAll(statusMatched);
        matched.addAll(summaryMatched);
        return List.copyOf(matched);
    }

    private String buildQaReason(boolean evidenceIntent) {
        if (evidenceIntent) {
            return "\u547d\u4e2d\u8bc1\u636e\u3001\u5f15\u7528\u6216\u539f\u6587\u7c7b\u9700\u6c42\uff0c\u9700\u4fdd\u7559\u5f15\u7528\u94fe\u8def\u5e76\u56de\u7b54\u5177\u4f53\u95ee\u9898\uff0c\u56e0\u6b64\u4f18\u5148\u8def\u7531\u5230 QA \u5de5\u5177\u3002";
        }
        return "\u672a\u547d\u4e2d\u4ec5\u67e5\u72b6\u6001\u6216\u6458\u8981\u7684\u660e\u786e\u610f\u56fe\uff0c\u9ed8\u8ba4\u6309\u6587\u6863\u95ee\u7b54\u5904\u7406\uff0c\u4ee5\u4fbf\u8fd4\u56de\u66f4\u5177\u4f53\u7684\u5185\u5bb9\u56de\u7b54\u3002";
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
