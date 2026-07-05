package com.docpilot.backend.quality.vo;

import java.util.List;

public record QualityRunDetail(
        QualityRunSummary summary,
        List<QualityGateSummary> gates,
        List<QualityEvalCaseResultDetail> evalCases,
        List<QualityTraceReference> traceReferences
) {

    public QualityRunDetail {
        gates = gates == null ? List.of() : List.copyOf(gates);
        evalCases = evalCases == null ? List.of() : List.copyOf(evalCases);
        traceReferences = traceReferences == null ? List.of() : List.copyOf(traceReferences);
    }

    public QualityRunDetail(
            QualityRunSummary summary,
            List<QualityGateSummary> gates,
            List<QualityEvalCaseResultDetail> evalCases) {
        this(summary, gates, evalCases, List.of());
    }
}
