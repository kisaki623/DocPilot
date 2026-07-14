package com.docpilot.backend.quality.eval;

import com.docpilot.backend.quality.vo.QualityEvalCaseResultDetail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentQualityEvalResult(
        String status,
        AgentQualityEvalMetrics metrics,
        List<QualityEvalCaseResultDetail> caseResults
) {

    public AgentQualityEvalResult {
        status = status == null || status.isBlank() ? "REVIEW" : status.trim();
        caseResults = caseResults == null ? List.of() : List.copyOf(caseResults);
        metrics = metrics == null ? AgentQualityEvalMetrics.from(caseResults) : metrics;
    }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("status", status);
        value.put("metrics", metrics);
        value.put("caseResults", caseResults);
        value.put("rawQuestionStored", false);
        value.put("rawAnswerStored", false);
        value.put("rawEvidenceStored", false);
        return value;
    }
}
