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
            "evidence", "citation", "cite", "proof", "\u4f9d\u636e", "\u5f15\u7528", "\u51fa\u5904", "\u8bc1\u636e"
    );

    @Override
    public SelectResult select(String task) {
        boolean statusIntent = containsAnyKeyword(task, STATUS_KEYWORDS);
        boolean summaryIntent = containsAnyKeyword(task, SUMMARY_KEYWORDS);
        boolean evidenceIntent = containsAnyKeyword(task, EVIDENCE_KEYWORDS);

        if (statusIntent && !summaryIntent && !evidenceIntent) {
            return new SelectResult("status_only", List.of("document_status_tool"));
        }
        if (summaryIntent && !evidenceIntent) {
            return new SelectResult("summary_tool", List.of("document_status_tool", "document_summary_tool"));
        }
        return new SelectResult("qa_tool", List.of("document_status_tool", "document_qa_tool"));
    }

    private boolean containsAnyKeyword(String task, List<String> keywords) {
        String normalized = safeText(task).toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
