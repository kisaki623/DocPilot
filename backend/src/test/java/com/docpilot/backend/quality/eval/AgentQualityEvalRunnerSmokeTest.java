package com.docpilot.backend.quality.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AgentQualityEvalRunnerSmokeTest {

    @Test
    void shouldWriteRedactedArtifactWhenEnabled() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("DOCPILOT_AGENT_QUALITY_EVAL_ENABLED")));
        String artifactPath = System.getenv("DOCPILOT_AGENT_QUALITY_EVAL_ARTIFACT");
        assumeTrue(artifactPath != null && !artifactPath.isBlank());

        String marker = System.getenv("DOCPILOT_AGENT_QUALITY_EVAL_MARKER");
        if (marker == null || marker.isBlank()) {
            marker = "docpilot-agent-quality-eval-local";
        }

        AgentQualityEvalRunner runner = new AgentQualityEvalRunner();
        AgentQualityEvalResult result = runner.evaluateDefaultCases();
        AgentQualityEvalMetrics metrics = result.metrics();

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("smokeMarker", marker);
        artifact.put("status", result.status());
        artifact.put("agentQualityEval", Map.of(
                "status", result.status(),
                "passed", metrics.failedCaseCount() == 0,
                "caseCount", metrics.caseCount(),
                "passedCaseCount", metrics.passedCaseCount(),
                "failedCaseCount", metrics.failedCaseCount(),
                "casePassRate", metrics.casePassRate(),
                "traceLinkedCaseCount", metrics.traceLinkedCaseCount(),
                "failureBuckets", metrics.failedCaseCount() == 0 ? List.of() : List.of("agentQualityEvalFailed")
        ));
        artifact.put("caseResults", result.caseResults());
        artifact.put("rawQuestionStored", false);
        artifact.put("rawAnswerStored", false);
        artifact.put("rawEvidenceStored", false);

        new ObjectMapper().findAndRegisterModules()
                .writerWithDefaultPrettyPrinter()
                .writeValue(Path.of(artifactPath).toFile(), artifact);
    }
}
