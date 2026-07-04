package com.docpilot.backend.quality.eval;

import java.util.Set;

public record AgentQualityEvalObservation(
        String caseId,
        Set<String> observedEvidence,
        Set<String> observedTools,
        String sanitizedOutput,
        String traceId,
        String agentRunId
) {

    public AgentQualityEvalObservation {
        caseId = clean(caseId);
        observedEvidence = observedEvidence == null ? Set.of() : Set.copyOf(observedEvidence);
        observedTools = observedTools == null ? Set.of() : Set.copyOf(observedTools);
        sanitizedOutput = clean(sanitizedOutput);
        traceId = clean(traceId);
        agentRunId = clean(agentRunId);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
