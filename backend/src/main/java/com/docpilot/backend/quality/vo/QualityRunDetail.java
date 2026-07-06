package com.docpilot.backend.quality.vo;

import java.util.List;

public record QualityRunDetail(
        QualityRunSummary summary,
        List<QualityGateSummary> gates,
        List<QualityEvalCaseResultDetail> evalCases,
        List<QualityTraceReference> traceReferences,
        QualityRunDiagnostics diagnostics
) {

    public QualityRunDetail {
        gates = gates == null ? List.of() : List.copyOf(gates);
        evalCases = evalCases == null ? List.of() : List.copyOf(evalCases);
        traceReferences = traceReferences == null ? List.of() : List.copyOf(traceReferences);
        diagnostics = diagnostics == null ? QualityRunDiagnostics.empty() : diagnostics;
    }

    public QualityRunDetail(
            QualityRunSummary summary,
            List<QualityGateSummary> gates,
            List<QualityEvalCaseResultDetail> evalCases) {
        this(summary, gates, evalCases, List.of(), QualityRunDiagnostics.empty());
    }

    public QualityRunDetail(
            QualityRunSummary summary,
            List<QualityGateSummary> gates,
            List<QualityEvalCaseResultDetail> evalCases,
            List<QualityTraceReference> traceReferences) {
        this(summary, gates, evalCases, traceReferences, QualityRunDiagnostics.empty());
    }
}
