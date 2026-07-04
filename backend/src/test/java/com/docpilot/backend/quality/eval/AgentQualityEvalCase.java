package com.docpilot.backend.quality.eval;

import java.util.List;
import java.util.Map;

public record AgentQualityEvalCase(
        String caseId,
        String question,
        String expectedBehavior,
        List<String> expectedEvidence,
        List<String> expectedTools,
        List<String> mustContain,
        List<String> mustNotContain,
        List<String> tags,
        Map<String, Object> scoringRules
) {

    public AgentQualityEvalCase {
        caseId = clean(caseId);
        question = clean(question);
        expectedBehavior = clean(expectedBehavior);
        expectedEvidence = safeList(expectedEvidence);
        expectedTools = safeList(expectedTools);
        mustContain = safeList(mustContain);
        mustNotContain = safeList(mustNotContain);
        tags = safeList(tags);
        scoringRules = scoringRules == null ? Map.of() : Map.copyOf(scoringRules);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .map(AgentQualityEvalCase::clean)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
