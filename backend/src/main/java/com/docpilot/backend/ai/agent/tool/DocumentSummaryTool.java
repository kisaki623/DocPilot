package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.stereotype.Component;

@Component
public class DocumentSummaryTool implements AgentTool<DocumentSummaryTool.SummaryInput, DocumentSummaryTool.SummaryResult> {

    private static final int SUMMARY_FALLBACK_MAX_LENGTH = 320;

    @Override
    public String getToolName() {
        return "document_summary_tool";
    }

    @Override
    public SummaryResult execute(SummaryInput input) {
        ValidationUtils.requireNonNull(input, "input");
        ValidationUtils.requireNonBlank(input.task(), "task");

        String summary = normalizeText(input.summary());
        if (!summary.isEmpty()) {
            return new SummaryResult(summary, "summary_field");
        }

        String content = normalizeText(input.content());
        if (content.isEmpty()) {
            return new SummaryResult("当前文档暂无可用摘要内容，请先确认解析结果。", "empty");
        }
        if (content.length() > SUMMARY_FALLBACK_MAX_LENGTH) {
            content = content.substring(0, SUMMARY_FALLBACK_MAX_LENGTH) + "...";
        }
        return new SummaryResult(content, "content_fallback");
    }

    private String normalizeText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\s+", " ").trim();
    }

    public record SummaryInput(String task, String summary, String content) {
    }

    public record SummaryResult(String output, String source) {
    }
}
