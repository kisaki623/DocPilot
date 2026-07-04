package com.docpilot.backend.quality.vo;

import java.util.List;

public record QualityRunDetail(
        QualityRunSummary summary,
        List<QualityGateSummary> gates,
        List<QualityEvalCaseResultDetail> evalCases
) {

    public QualityRunDetail {
        gates = gates == null ? List.of() : List.copyOf(gates);
        evalCases = evalCases == null ? List.of() : List.copyOf(evalCases);
    }
}
