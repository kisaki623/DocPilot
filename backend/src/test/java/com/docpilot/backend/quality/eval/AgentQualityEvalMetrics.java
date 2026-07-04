package com.docpilot.backend.quality.eval;

import com.docpilot.backend.quality.vo.QualityEvalCaseResultDetail;

import java.util.List;

public record AgentQualityEvalMetrics(
        int caseCount,
        int passedCaseCount,
        int failedCaseCount,
        double casePassRate,
        int traceLinkedCaseCount
) {

    public static AgentQualityEvalMetrics from(List<QualityEvalCaseResultDetail> results) {
        List<QualityEvalCaseResultDetail> resolved = results == null ? List.of() : List.copyOf(results);
        int passed = (int) resolved.stream().filter(result -> Boolean.TRUE.equals(result.passed())).count();
        int traceLinked = (int) resolved.stream()
                .filter(result -> !result.traceId().isBlank() || !result.agentRunId().isBlank())
                .count();
        int caseCount = resolved.size();
        return new AgentQualityEvalMetrics(
                caseCount,
                passed,
                caseCount - passed,
                caseCount == 0 ? 1.0D : (double) passed / caseCount,
                traceLinked
        );
    }
}
