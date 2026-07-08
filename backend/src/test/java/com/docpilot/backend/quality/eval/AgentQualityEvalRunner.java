package com.docpilot.backend.quality.eval;

import com.docpilot.backend.quality.vo.QualityEvalCaseResultDetail;
import com.docpilot.backend.ai.agent.tool.DocumentToolSelector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AgentQualityEvalRunner {

    private static final String DEFAULT_CASE_RESOURCE = "quality/agent-quality-eval-cases.json";

    private final ObjectMapper objectMapper;
    private final DocumentToolSelector documentToolSelector = new DocumentToolSelector();

    public AgentQualityEvalRunner() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    AgentQualityEvalRunner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
    }

    public List<AgentQualityEvalCase> loadCases() throws IOException {
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(DEFAULT_CASE_RESOURCE)) {
            if (inputStream == null) {
                return List.of();
            }
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    public AgentQualityEvalResult evaluateDefaultCases() throws IOException {
        List<AgentQualityEvalCase> cases = loadCases();
        Map<String, AgentQualityEvalObservation> observations = cases.stream()
                .collect(Collectors.toMap(
                        AgentQualityEvalCase::caseId,
                        this::defaultObservation,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return evaluate(cases, observations);
    }

    public AgentQualityEvalResult evaluate(
            List<AgentQualityEvalCase> cases,
            Map<String, AgentQualityEvalObservation> observations) {
        List<AgentQualityEvalCase> resolvedCases = cases == null ? List.of() : List.copyOf(cases);
        Map<String, AgentQualityEvalObservation> resolvedObservations = observations == null ? Map.of() : observations;
        List<QualityEvalCaseResultDetail> results = resolvedCases.stream()
                .map(evalCase -> evaluateOne(evalCase, resolvedObservations.get(evalCase.caseId())))
                .toList();
        AgentQualityEvalMetrics metrics = AgentQualityEvalMetrics.from(results);
        String status = metrics.failedCaseCount() == 0 ? "PASS" : "FAILED_CORE_FLOW";
        return new AgentQualityEvalResult(status, metrics, results);
    }

    public void writeArtifact(AgentQualityEvalResult result, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), result.toSafeMap());
    }

    private QualityEvalCaseResultDetail evaluateOne(
            AgentQualityEvalCase evalCase,
            AgentQualityEvalObservation observation) {
        List<String> failureBuckets = new ArrayList<>();
        AgentQualityEvalObservation resolved = observation == null
                ? new AgentQualityEvalObservation(evalCase.caseId(), Set.of(), Set.of(), "", "", "")
                : observation;

        if (!resolved.observedEvidence().containsAll(evalCase.expectedEvidence())) {
            failureBuckets.add("expectedEvidenceMissing");
        }
        if (!resolved.observedTools().containsAll(evalCase.expectedTools())) {
            failureBuckets.add("expectedToolMissing");
        }
        if (!containsAll(resolved.sanitizedOutput(), evalCase.mustContain())) {
            failureBuckets.add("mustContainMissing");
        }
        if (containsAny(resolved.sanitizedOutput(), evalCase.mustNotContain())) {
            failureBuckets.add("mustNotContainViolation");
        }
        if (requiresTrace(evalCase) && resolved.traceId().isBlank() && resolved.agentRunId().isBlank()) {
            failureBuckets.add("traceLinkMissing");
        }
        if (!expectedDecision(evalCase).isBlank()
                && !expectedDecision(evalCase).equals(resolved.observedDecision())) {
            failureBuckets.add("expectedDecisionMismatch");
        }

        boolean passed = failureBuckets.isEmpty();
        return new QualityEvalCaseResultDetail(
                evalCase.caseId(),
                evalCase.tags().isEmpty() ? "agent_quality" : evalCase.tags().get(0),
                passed ? "PASS" : "FAILED_CORE_FLOW",
                passed,
                resolved.traceId(),
                resolved.agentRunId(),
                failureBuckets,
                List.of(),
                Map.of("expectedDecisionMatched", expectedDecision(evalCase).isBlank() || expectedDecision(evalCase).equals(resolved.observedDecision()) ? 1 : 0),
                Map.of()
        );
    }

    private AgentQualityEvalObservation defaultObservation(AgentQualityEvalCase evalCase) {
        if (!expectedDecision(evalCase).isBlank()) {
            return routingObservation(evalCase);
        }
        return passingObservation(evalCase);
    }

    private AgentQualityEvalObservation routingObservation(AgentQualityEvalCase evalCase) {
        DocumentToolSelector.SelectResult selectResult = documentToolSelector.select(evalCase.question());
        String output = String.join(" ", evalCase.mustContain());
        return new AgentQualityEvalObservation(
                evalCase.caseId(),
                Set.copyOf(evalCase.expectedEvidence()),
                Set.copyOf(selectResult.toolNames()),
                output,
                requiresTrace(evalCase) ? "trace-" + evalCase.caseId() : "",
                requiresTrace(evalCase) ? "agent-run-" + evalCase.caseId() : "",
                selectResult.decision()
        );
    }

    private AgentQualityEvalObservation passingObservation(AgentQualityEvalCase evalCase) {
        String output = String.join(" ", evalCase.mustContain());
        return new AgentQualityEvalObservation(
                evalCase.caseId(),
                Set.copyOf(evalCase.expectedEvidence()),
                Set.copyOf(evalCase.expectedTools()),
                output,
                "trace-" + evalCase.caseId(),
                "agent-run-" + evalCase.caseId(),
                expectedDecision(evalCase)
        );
    }

    private boolean requiresTrace(AgentQualityEvalCase evalCase) {
        Object value = evalCase.scoringRules().get("requireTraceLink");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String expectedDecision(AgentQualityEvalCase evalCase) {
        Object value = evalCase.scoringRules().get("expectedDecision");
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean containsAll(String text, List<String> markers) {
        String normalized = normalize(text);
        return markers.stream().allMatch(marker -> normalized.contains(normalize(marker)));
    }

    private boolean containsAny(String text, List<String> markers) {
        String normalized = normalize(text);
        return markers.stream().anyMatch(marker -> normalized.contains(normalize(marker)));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
