package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.service.QualityArtifactService;
import com.docpilot.backend.quality.vo.QualityEvalCaseResultDetail;
import com.docpilot.backend.quality.vo.QualityGateSummary;
import com.docpilot.backend.quality.vo.QualityRunDiagnostics;
import com.docpilot.backend.quality.vo.QualityRunDetail;
import com.docpilot.backend.quality.vo.QualityRunSummary;
import com.docpilot.backend.quality.vo.QualityTokenUsageSummary;
import com.docpilot.backend.quality.vo.QualityTraceReference;
import com.docpilot.backend.quality.vo.QualityTraceStepDetail;
import com.docpilot.backend.quality.vo.QualityTrendPoint;
import com.docpilot.backend.quality.vo.QualityTrendSummary;
import com.docpilot.backend.quality.vo.QualityRepeatedCaseSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class QualityArtifactServiceImpl implements QualityArtifactService {

    private static final List<String> ARTIFACT_ROOTS = List.of(
            "backend/target/audit",
            "backend/target/rag-natural-corpus",
            "backend/target/rag-real-qa",
            "backend/target/smoke/document-parser-real-chain",
            "backend/target/conversation-grounding",
            "backend/target/memory-quality",
            "backend/target/memory-provider",
            "backend/target/agent-quality-eval",
            "backend/target/agent-search-route",
            "backend/target/agent-kb-search-route",
            "tmp-e2e/docpilot-cloud-quality-smoke"
    );
    private static final Set<String> ARTIFACT_FILE_NAMES = Set.of(
            "artifact.json",
            "real-experience-audit-report.json"
    );
    private static final Set<String> TOP_LEVEL_SKIP_FIELDS = Set.of(
            "documents",
            "users",
            "requests",
            "responses",
            "raw",
            "logs"
    );
    private static final List<String> CASE_ARRAY_FIELD_NAMES = List.of(
            "caseResults",
            "caseEvaluations",
            "evalCases",
            "cases"
    );

    private final Path repoRoot;
    private final ObjectMapper objectMapper;

    @Autowired
    public QualityArtifactServiceImpl(ObjectMapper objectMapper) {
        this(resolveRepoRoot(), objectMapper);
    }

    QualityArtifactServiceImpl(Path repoRoot, ObjectMapper objectMapper) {
        this.repoRoot = normalizeRepoRoot(repoRoot == null ? Path.of("") : repoRoot);
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public List<QualityRunSummary> listRecentRuns(int limit) {
        int resolvedLimit = limit <= 0 ? DEFAULT_LIMIT : limit;
        return discoverArtifacts().stream()
                .sorted(Comparator.comparing(QualityArtifactFile::updatedAt).reversed())
                .limit(resolvedLimit)
                .map(file -> parseDetail(file).summary())
                .toList();
    }

    @Override
    public Optional<QualityRunDetail> getRunDetail(String marker) {
        if (marker == null || marker.trim().isEmpty()) {
            return Optional.empty();
        }
        String expected = marker.trim();
        return discoverArtifacts().stream()
                .map(this::parseDetail)
                .filter(detail -> expected.equals(detail.summary().marker()))
                .findFirst();
    }

    @Override
    public QualityTrendSummary getTrendSummary(int limit) {
        int resolvedLimit = limit <= 0 ? DEFAULT_LIMIT : limit;
        List<QualityRunDetail> details = discoverArtifacts().stream()
                .sorted(Comparator.comparing(QualityArtifactFile::updatedAt).reversed())
                .limit(resolvedLimit)
                .map(this::parseDetail)
                .toList();
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        Map<String, Integer> failureBucketCounts = new LinkedHashMap<>();
        Map<String, Integer> reviewBucketCounts = new LinkedHashMap<>();
        CaseTrendAccumulator caseAccumulator = new CaseTrendAccumulator();
        List<QualityTrendPoint> points = new ArrayList<>();
        int totalTokens = 0;
        boolean hasTokens = false;
        BigDecimal estimatedCost = BigDecimal.ZERO;
        boolean hasCost = false;
        double casePassRateSum = 0.0;
        int casePassRateCount = 0;
        double latencySum = 0.0;
        int latencyCount = 0;
        double durationSum = 0.0;
        int durationCount = 0;

        for (QualityRunDetail detail : details) {
            QualityRunSummary summary = detail.summary();
            increment(statusCounts, summary.status());
            summary.failureBuckets().forEach(bucket -> increment(failureBucketCounts, bucket));
            summary.reviewBuckets().forEach(bucket -> increment(reviewBucketCounts, bucket));
            detail.evalCases().forEach(item -> {
                item.failureBuckets().forEach(bucket -> increment(failureBucketCounts, bucket));
                item.reviewBuckets().forEach(bucket -> increment(reviewBucketCounts, bucket));
            });
            Double casePassRate = findMetric(detail, "casePassRate");
            if (casePassRate != null) {
                casePassRateSum += casePassRate;
                casePassRateCount++;
            }
            Double latencyMs = sumMetric(detail, "latencyMs");
            if (latencyMs != null) {
                latencySum += latencyMs;
                latencyCount++;
            }
            Double durationMs = sumMetric(detail, "durationMs");
            if (durationMs != null) {
                durationSum += durationMs;
                durationCount++;
            }
            Integer runTokens = summary.tokenUsage().totalTokens();
            if (runTokens != null) {
                totalTokens += runTokens;
                hasTokens = true;
            }
            if (summary.tokenUsage().estimatedCost() != null) {
                estimatedCost = estimatedCost.add(summary.tokenUsage().estimatedCost());
                hasCost = true;
            }
            caseAccumulator.accept(detail);
            points.add(new QualityTrendPoint(
                    summary.marker(),
                    summary.status(),
                    summary.updatedAt(),
                    summary.failedGateCount(),
                    summary.reviewGateCount(),
                    casePassRate,
                    runTokens,
                    summary.tokenUsage().estimatedCost() == null ? null : summary.tokenUsage().estimatedCost().doubleValue(),
                    latencyMs,
                    durationMs,
                    summary.failureBuckets(),
                    summary.reviewBuckets()
            ));
        }

        return new QualityTrendSummary(
                resolvedLimit,
                details.size(),
                statusCounts,
                failureBucketCounts,
                reviewBucketCounts,
                average(casePassRateSum, casePassRateCount),
                hasTokens ? totalTokens : null,
                hasCost ? estimatedCost.doubleValue() : null,
                average(latencySum, latencyCount),
                average(durationSum, durationCount),
                caseAccumulator.repeatedCases(),
                points
        );
    }

    private void increment(Map<String, Integer> counts, String key) {
        String resolved = key == null || key.isBlank() ? "UNKNOWN" : key;
        counts.merge(resolved, 1, Integer::sum);
    }

    private Double findMetric(QualityRunDetail detail, String name) {
        String expected = normalizeFieldName(name);
        for (QualityGateSummary gate : detail.gates()) {
            for (Map.Entry<String, Number> entry : gate.metrics().entrySet()) {
                if (expected.equals(normalizeFieldName(entry.getKey()))) {
                    return entry.getValue().doubleValue();
                }
            }
        }
        for (QualityEvalCaseResultDetail item : detail.evalCases()) {
            for (Map.Entry<String, Number> entry : item.metrics().entrySet()) {
                if (expected.equals(normalizeFieldName(entry.getKey()))) {
                    return entry.getValue().doubleValue();
                }
            }
        }
        return null;
    }

    private Double sumMetric(QualityRunDetail detail, String name) {
        String expected = normalizeFieldName(name);
        double sum = 0.0;
        boolean found = false;
        for (QualityGateSummary gate : detail.gates()) {
            for (Map.Entry<String, Number> entry : gate.metrics().entrySet()) {
                if (expected.equals(normalizeFieldName(entry.getKey()))) {
                    sum += entry.getValue().doubleValue();
                    found = true;
                }
            }
        }
        for (QualityEvalCaseResultDetail item : detail.evalCases()) {
            for (Map.Entry<String, Number> entry : item.metrics().entrySet()) {
                if (expected.equals(normalizeFieldName(entry.getKey()))) {
                    sum += entry.getValue().doubleValue();
                    found = true;
                }
            }
        }
        return found ? sum : null;
    }

    private Double average(double sum, int count) {
        return count <= 0 ? null : sum / count;
    }

    private List<QualityArtifactFile> discoverArtifacts() {
        List<QualityArtifactFile> files = new ArrayList<>();
        for (String root : ARTIFACT_ROOTS) {
            Path rootPath = repoRoot.resolve(root).normalize();
            if (!Files.isDirectory(rootPath)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(rootPath, 4)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> ARTIFACT_FILE_NAMES.contains(path.getFileName().toString()))
                        .forEach(path -> files.add(new QualityArtifactFile(
                                root,
                                rootPath.relativize(path).toString().replace('\\', '/'),
                                path,
                                lastModified(path)
                        )));
            } catch (IOException ignored) {
                // Unreadable artifact roots should not break the internal console overview.
            }
        }
        return files;
    }

    private QualityRunDetail parseDetail(QualityArtifactFile file) {
        try {
            JsonNode root = objectMapper.readTree(file.path().toFile());
            if (root == null || !root.isObject()) {
                return parseFailedDetail(file);
            }
            return safeDetail(file, root);
        } catch (Exception ignored) {
            return parseFailedDetail(file);
        }
    }

    private QualityRunDetail safeDetail(QualityArtifactFile file, JsonNode root) {
        List<QualityGateSummary> extractedGates = extractGates(root);
        List<QualityGateSummary> gates = extractedGates.isEmpty() && root.path("cases").isArray()
                ? List.of(toGateSummary(rootGateName(root), root))
                : extractedGates;
        List<String> failureBuckets = mergeBuckets(root, gates, "failureBuckets");
        List<String> reviewBuckets = mergeBuckets(root, gates, "reviewBuckets");
        QualityTokenUsageSummary tokenUsage = extractTokenUsage(root);
        String marker = firstText(root, "smokeMarker", "marker", "runMarker")
                .orElseGet(() -> markerFromPath(file));
        String status = firstText(root, "status", "overallStatus", "result")
                .orElseGet(() -> inferStatus(gates, failureBuckets, reviewBuckets));
        int failedGateCount = (int) gates.stream()
                .filter(gate -> hasFailures(gate) || "FAILED".equalsIgnoreCase(gate.status()))
                .count();
        int reviewGateCount = (int) gates.stream()
                .filter(gate -> !gate.reviewBuckets().isEmpty() || "REVIEW".equalsIgnoreCase(gate.status()))
                .count();
        QualityRunSummary summary = new QualityRunSummary(
                marker,
                file.sourceRoot(),
                file.relativePath(),
                normalizeStatus(status),
                file.updatedAt(),
                gates.size(),
                failedGateCount,
                reviewGateCount,
                failureBuckets,
                reviewBuckets,
                tokenUsage,
                false,
                false
        );
        List<ExtractedEvalCase> extractedEvalCases = extractEvalCaseNodes(root);
        List<QualityEvalCaseResultDetail> evalCases = toEvalCaseDetails(extractedEvalCases);
        List<QualityTraceReference> traceReferences = mergeTraceReferences(
                toTraceReferences(extractedEvalCases),
                toGateTraceReferences(root, gates)
        );
        return new QualityRunDetail(summary, gates, evalCases, traceReferences,
                buildDiagnostics(root, gates, evalCases, traceReferences));
    }

    private QualityRunDetail parseFailedDetail(QualityArtifactFile file) {
        List<String> failureBuckets = List.of("artifactParseFailed");
        QualityRunSummary summary = new QualityRunSummary(
                markerFromPath(file),
                file.sourceRoot(),
                file.relativePath(),
                "REVIEW",
                file.updatedAt(),
                0,
                0,
                1,
                failureBuckets,
                List.of(),
                QualityTokenUsageSummary.empty(),
                false,
                true
        );
        return new QualityRunDetail(summary, List.of(), List.of());
    }

    private List<QualityGateSummary> extractGates(JsonNode root) {
        List<QualityGateSummary> gates = new ArrayList<>();
        JsonNode nestedGates = root.path("gates");
        if (nestedGates.isObject()) {
            nestedGates.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isObject() && looksLikeGate(value)) {
                    gates.add(toGateSummary(entry.getKey(), value));
                }
            });
        }
        root.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            JsonNode value = entry.getValue();
            if (!value.isObject() || "gates".equals(name) || TOP_LEVEL_SKIP_FIELDS.contains(name)) {
                return;
            }
            if (!looksLikeGate(value)) {
                return;
            }
            gates.add(toGateSummary(name, value));
        });
        return gates;
    }

    private boolean looksLikeGate(JsonNode node) {
        return node.has("status")
                || node.has("passed")
                || node.has("pass")
                || node.has("failureBuckets")
                || node.has("hardFailureBuckets")
                || node.has("reviewBuckets")
                || node.has("checks")
                || node.has("casePassRate")
                || node.has("modelCallCount")
                || node.has("evidenceCount");
    }

    private QualityGateSummary toGateSummary(String name, JsonNode node) {
        Map<String, Number> metrics = safeMetrics(node);
        Map<String, Boolean> flags = safeFlags(node);
        mergeCheckSummaries(node.path("checks"), metrics, flags);
        mergeCaseSummaries(firstDirectCaseArray(node), metrics);
        Boolean passed = optionalBoolean(node, "passed")
                .or(() -> optionalBoolean(node, "pass"))
                .orElse(null);
        return new QualityGateSummary(
                name,
                firstText(node, "status").map(this::normalizeStatus).orElse(null),
                passed,
                metrics,
                flags,
                gateFailureBuckets(node),
                stringList(node.path("reviewBuckets"))
        );
    }

    private List<ExtractedEvalCase> extractEvalCaseNodes(JsonNode root) {
        List<ExtractedEvalCase> results = new ArrayList<>();
        collectEvalCaseNodes(root, "", "", results);
        return results;
    }

    private void collectEvalCaseNodes(JsonNode node,
                                      String currentName,
                                      String gateName,
                                      List<ExtractedEvalCase> results) {
        if (node == null || !node.isContainerNode()) {
            return;
        }
        if (node.isObject()) {
            String resolvedGateName = gateName;
            if (resolvedGateName.isBlank() && !currentName.isBlank() && looksLikeGate(node)) {
                resolvedGateName = currentName;
            }
            for (String fieldName : CASE_ARRAY_FIELD_NAMES) {
                JsonNode cases = node.get(fieldName);
                if (cases != null && cases.isArray()) {
                    for (JsonNode item : cases) {
                        if (item.isObject()) {
                            results.add(new ExtractedEvalCase(item, resolvedGateName));
                        }
                    }
                }
            }
            String nextGateName = resolvedGateName;
            node.fields().forEachRemaining(entry -> {
                if (!TOP_LEVEL_SKIP_FIELDS.contains(entry.getKey())
                        && !isSensitiveField(entry.getKey())
                        && entry.getValue().isContainerNode()) {
                    collectEvalCaseNodes(entry.getValue(), entry.getKey(), nextGateName, results);
                }
            });
            return;
        }
        for (JsonNode item : node) {
            collectEvalCaseNodes(item, currentName, gateName, results);
        }
    }

    private List<QualityEvalCaseResultDetail> toEvalCaseDetails(List<ExtractedEvalCase> extractedEvalCases) {
        List<QualityEvalCaseResultDetail> results = new ArrayList<>();
        for (ExtractedEvalCase extracted : extractedEvalCases) {
            JsonNode item = extracted.node();
            if (!item.isObject()) {
                continue;
            }
            String caseId = firstText(item, "caseId").orElse(null);
            if (caseId == null || caseId.isBlank()) {
                continue;
            }
            Boolean passed = optionalBoolean(item, "passed")
                    .or(() -> optionalBoolean(item, "pass"))
                    .orElse(null);
            results.add(new QualityEvalCaseResultDetail(
                    caseId,
                    firstText(item, "caseType", "category", "groundingPolicy").orElse(null),
                    evalCaseStatus(item),
                    passed,
                    firstScalarText(item, "traceId").orElse(null),
                    firstScalarText(item, "agentRunId").orElse(null),
                    stringList(item.path("failureBuckets")),
                    stringList(item.path("reviewBuckets")),
                    safeMetrics(item),
                    safeFlags(item)
            ));
        }
        return results;
    }

    private List<QualityTraceReference> toTraceReferences(List<ExtractedEvalCase> extractedEvalCases) {
        List<QualityTraceReference> references = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ExtractedEvalCase extracted : extractedEvalCases) {
            JsonNode item = extracted.node();
            String caseId = firstText(item, "caseId").orElse("");
            if (caseId.isBlank()) {
                continue;
            }
            String traceId = firstScalarText(item, "traceId").orElse("");
            String agentRunId = firstScalarText(item, "agentRunId").orElse("");
            List<String> failureBuckets = stringList(item.path("failureBuckets"));
            List<String> reviewBuckets = stringList(item.path("reviewBuckets"));
            String status = evalCaseStatus(item);
            Map<String, Number> metrics = safeMetrics(item);
            Map<String, Boolean> flags = safeFlags(item);
            boolean hasLocator = !traceId.isBlank() || !agentRunId.isBlank();
            boolean needsAttention = !failureBuckets.isEmpty()
                    || !reviewBuckets.isEmpty()
                    || "REVIEW".equals(status)
                    || status.startsWith("FAILED");
            if (!hasLocator && !needsAttention) {
                continue;
            }
            String dedupeKey = caseId + "|" + traceId + "|" + agentRunId;
            if (!seen.add(dedupeKey)) {
                continue;
            }
            references.add(new QualityTraceReference(
                    caseId,
                    firstText(item, "caseType", "category", "groundingPolicy").orElse(null),
                    status,
                    extracted.gateName(),
                    traceId,
                    agentRunId,
                    firstScalarText(item, "conversationId").orElse(null),
                    failureBuckets,
                    reviewBuckets,
                    buildTraceSteps(item, status, metrics, flags, failureBuckets, reviewBuckets, hasLocator)
            ));
        }
        return references.stream()
                .sorted(Comparator.comparingInt(this::traceReferencePriority))
                .toList();
    }

    private List<QualityTraceReference> toGateTraceReferences(JsonNode root, List<QualityGateSummary> gates) {
        List<QualityTraceReference> references = new ArrayList<>();
        knowledgeBaseAgentGateNode(root).ifPresent(node -> {
            QualityGateSummary gate = gates.stream()
                    .filter(item -> "knowledgeBaseAgent".equals(item.name()))
                    .findFirst()
                    .orElseGet(() -> toGateSummary("knowledgeBaseAgent", node));
            JsonNode check = firstObject(node.path("checks")).orElse(node);
            String status = gate.status() == null || gate.status().isBlank() ? "REVIEW" : gate.status();
            references.add(new QualityTraceReference(
                    "knowledge-base-agent-runtime",
                    "agent_kb_runtime",
                    status,
                    "knowledgeBaseAgent",
                    firstScalarText(check, "traceId").orElse(""),
                    firstScalarText(check, "agentRunId").orElse(""),
                    firstScalarText(check, "conversationId").orElse(""),
                    gate.failureBuckets(),
                    gate.reviewBuckets(),
                    buildKnowledgeBaseAgentTraceSteps(check, gate, status)
            ));
        });
        return references;
    }

    private Optional<JsonNode> knowledgeBaseAgentGateNode(JsonNode root) {
        JsonNode nested = root.path("gates").path("knowledgeBaseAgent");
        if (nested.isObject()) {
            return Optional.of(nested);
        }
        JsonNode topLevel = root.path("knowledgeBaseAgent");
        if (topLevel.isObject()) {
            return Optional.of(topLevel);
        }
        return Optional.empty();
    }

    private Optional<JsonNode> firstObject(JsonNode node) {
        if (!node.isArray()) {
            return Optional.empty();
        }
        for (JsonNode item : node) {
            if (item.isObject()) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    private List<QualityTraceReference> mergeTraceReferences(List<QualityTraceReference> first,
                                                             List<QualityTraceReference> second) {
        List<QualityTraceReference> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addTraceReferences(merged, seen, first);
        addTraceReferences(merged, seen, second);
        return merged.stream()
                .sorted(Comparator.comparingInt(this::traceReferencePriority))
                .toList();
    }

    private void addTraceReferences(List<QualityTraceReference> merged,
                                    Set<String> seen,
                                    List<QualityTraceReference> references) {
        if (references == null) {
            return;
        }
        for (QualityTraceReference reference : references) {
            String key = reference.caseId() + "|" + reference.traceId() + "|" + reference.agentRunId()
                    + "|" + reference.gateName();
            if (seen.add(key)) {
                merged.add(reference);
            }
        }
    }

    private List<QualityTraceStepDetail> buildKnowledgeBaseAgentTraceSteps(JsonNode check,
                                                                           QualityGateSummary gate,
                                                                           String status) {
        Map<String, Number> metrics = safeMetrics(check);
        Map<String, Boolean> flags = safeFlags(check);
        List<String> buckets = mergeLists(gate.failureBuckets(), gate.reviewBuckets());
        List<QualityTraceStepDetail> steps = new ArrayList<>();
        steps.add(traceStep("agent_step", status, "KB Agent route decision",
                metricsByName(metrics, "durationMs", "answerDurationMs"),
                flagsByName(flags, "success", "answerSuccess"),
                safeAttributes(check, "decision", "answerDecision")));
        steps.add(traceStep("tool_call", status, "KB Agent search tool",
                Map.of(),
                Map.of(),
                safeAttributes(check, "decision", "selectedTools")));
        steps.add(traceStep("rag_retrieve", status, "KB Agent search retrieval",
                metricsByName(metrics, "retrieveHits", "queryVariantCount"),
                flagsByName(flags, "coversBothDocuments", "rerankApplied", "multiQueryApplied"),
                Map.of()));
        steps.add(traceStep("tool_call", status, "KB Agent answer tool",
                metricsByName(metrics, "answerDurationMs"),
                flagsByName(flags, "answerSuccess"),
                safeAttributes(check, "answerDecision", "answerSelectedTools")));
        steps.add(traceStep("citation", status, "KB Agent citation",
                metricsByName(metrics, "citations", "answerCitations"),
                flagsByName(flags, "coversBothDocuments", "answerCoversBothDocuments", "answerNoEvidenceHandled",
                        "foreignKnowledgeBaseRejected"),
                Map.of()));
        if (!buckets.isEmpty()) {
            steps.add(traceStep("failure_bucket", status, "Failure bucket",
                    Map.of(),
                    Map.of(),
                    buckets));
        }
        return steps;
    }

    private List<QualityTraceStepDetail> buildTraceSteps(JsonNode item,
                                                         String status,
                                                         Map<String, Number> metrics,
                                                         Map<String, Boolean> flags,
                                                         List<String> failureBuckets,
                                                         List<String> reviewBuckets,
                                                         boolean hasLocator) {
        List<QualityTraceStepDetail> steps = new ArrayList<>();
        List<String> combinedBuckets = mergeLists(failureBuckets, reviewBuckets);
        String resolvedStatus = status == null || status.isBlank() ? "REVIEW" : status;
        steps.add(traceStep("eval_case", resolvedStatus, "Eval case",
                metricsByName(metrics, "casePassRate", "score"),
                flagsByName(flags, "expectedEvidenceSupported", "noEvidenceCorrect"),
                combinedBuckets));
        if (hasLocator) {
            steps.add(traceStep("agent_step", resolvedStatus, "Agent trace reference",
                    metricsByName(metrics, "durationMs", "latencyMs", "retryCount"),
                    flagsByName(flags, "ragTriggered", "ragRequired"),
                    List.of()));
        }
        Map<String, Number> retrievalMetrics = metricsBySuffix(metrics, "hits", "topK");
        if (!retrievalMetrics.isEmpty()
                || hasAnyFlag(flags, "ragTriggered", "ragRequired")
                || hasField(item, "retrieveHits", "retrievalHitCount", "evidenceCount")) {
            steps.add(traceStep("rag_retrieve", resolvedStatus, "RAG retrieval",
                    mergeMaps(retrievalMetrics, metricsByName(metrics, "retrieveHits", "evidenceCount", "documentHitCount")),
                    flagsByName(flags, "ragTriggered", "ragRequired"),
                    List.of()));
        }
        Map<String, Number> toolMetrics = metricsByName(metrics, "toolCallCount");
        if (!toolMetrics.isEmpty() || hasField(item, "toolCallCount", "toolName")) {
            steps.add(traceStep("tool_call", resolvedStatus, "Tool call",
                    toolMetrics,
                    Map.of(),
                    List.of()));
        }
        Map<String, Number> modelMetrics = metricsByName(metrics,
                "modelCallCount", "promptTokens", "completionTokens", "totalTokens",
                "estimatedCost", "latencyMs", "durationMs", "retryCount");
        if (!modelMetrics.isEmpty() || hasField(item, "modelCallCount", "totalTokens")) {
            steps.add(traceStep("model_call", resolvedStatus, "Model call",
                    modelMetrics,
                    Map.of(),
                    List.of()));
        }
        Map<String, Number> citationMetrics = mergeMaps(
                metricsBySuffix(metrics, "citations"),
                metricsByName(metrics, "qaCitations", "citationCount", "distractorCitationCount")
        );
        Map<String, Boolean> citationFlags = flagsByName(flags,
                "targetCitationCovered", "citationPhraseSupport", "citationMarkerPresent");
        if (!citationMetrics.isEmpty() || !citationFlags.isEmpty()) {
            steps.add(traceStep("citation", resolvedStatus, "Citation",
                    citationMetrics,
                    citationFlags,
                    List.of()));
        }
        if (!combinedBuckets.isEmpty()) {
            steps.add(traceStep("failure_bucket", resolvedStatus, "Failure bucket",
                    Map.of(),
                    Map.of(),
                    combinedBuckets));
        }
        return steps;
    }

    private QualityTraceStepDetail traceStep(String stepType,
                                             String status,
                                             String label,
                                             Map<String, Number> metrics,
                                             Map<String, Boolean> flags,
                                             List<String> buckets) {
        return traceStep(stepType, status, label, metrics, flags, Map.of(), buckets);
    }

    private QualityTraceStepDetail traceStep(String stepType,
                                             String status,
                                             String label,
                                             Map<String, Number> metrics,
                                             Map<String, Boolean> flags,
                                             Map<String, String> attributes) {
        return traceStep(stepType, status, label, metrics, flags, attributes, List.of());
    }

    private QualityTraceStepDetail traceStep(String stepType,
                                             String status,
                                             String label,
                                             Map<String, Number> metrics,
                                             Map<String, Boolean> flags,
                                             Map<String, String> attributes,
                                             List<String> buckets) {
        return new QualityTraceStepDetail(stepType, status, label, metrics, flags, attributes, buckets);
    }

    private Map<String, Number> metricsByName(Map<String, Number> metrics, String... names) {
        Map<String, Number> selected = new LinkedHashMap<>();
        Set<String> expected = new LinkedHashSet<>();
        for (String name : names) {
            expected.add(normalizeFieldName(name));
        }
        metrics.forEach((key, value) -> {
            if (expected.contains(normalizeFieldName(key))) {
                selected.put(key, value);
            }
        });
        return selected;
    }

    private Map<String, Number> metricsBySuffix(Map<String, Number> metrics, String... suffixes) {
        Map<String, Number> selected = new LinkedHashMap<>();
        metrics.forEach((key, value) -> {
            String normalized = normalizeFieldName(key);
            for (String suffix : suffixes) {
                if (normalized.endsWith(normalizeFieldName(suffix))) {
                    selected.put(key, value);
                    return;
                }
            }
        });
        return selected;
    }

    private Map<String, String> safeAttributes(JsonNode node, String... fieldNames) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (String fieldName : fieldNames) {
            if (isSensitiveField(fieldName)) {
                continue;
            }
            JsonNode value = node.path(fieldName);
            if (value.isTextual() && isSafeAttributeValue(value.asText())) {
                attributes.put(fieldName, value.asText());
            } else if (value.isNumber() || value.isBoolean()) {
                attributes.put(fieldName, value.asText());
            } else if (value.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode item : value) {
                    if (item.isTextual() && isSafeAttributeValue(item.asText())) {
                        values.add(item.asText());
                    }
                }
                if (!values.isEmpty()) {
                    attributes.put(fieldName, String.join(", ", values));
                }
            }
        }
        return attributes;
    }

    private boolean isSafeAttributeValue(String value) {
        if (value == null || value.isBlank() || value.length() > 160) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return !lower.contains("bearer ")
                && !lower.contains("sk-")
                && !lower.contains("jdbc:")
                && !lower.contains("http://")
                && !lower.contains("https://")
                && !lower.contains("password")
                && !lower.contains("secret")
                && !lower.contains("api key")
                && !lower.contains("api_key")
                && !lower.contains("apikey")
                && !lower.contains("prompt")
                && !lower.contains("document text")
                && !lower.contains("evidence context");
    }

    private Map<String, Boolean> flagsByName(Map<String, Boolean> flags, String... names) {
        Map<String, Boolean> selected = new LinkedHashMap<>();
        Set<String> expected = new LinkedHashSet<>();
        for (String name : names) {
            expected.add(normalizeFieldName(name));
        }
        flags.forEach((key, value) -> {
            if (expected.contains(normalizeFieldName(key))) {
                selected.put(key, value);
            }
        });
        return selected;
    }

    private boolean hasAnyFlag(Map<String, Boolean> flags, String... names) {
        return !flagsByName(flags, names).isEmpty();
    }

    private boolean hasField(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !isSensitiveField(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Number> mergeMaps(Map<String, Number> first, Map<String, Number> second) {
        Map<String, Number> merged = new LinkedHashMap<>(first);
        second.forEach(merged::putIfAbsent);
        return merged;
    }

    private List<String> mergeLists(List<String> first, List<String> second) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        return List.copyOf(merged);
    }

    private int traceReferencePriority(QualityTraceReference reference) {
        if (!reference.failureBuckets().isEmpty() || reference.status().startsWith("FAILED")) {
            return 0;
        }
        if (!reference.reviewBuckets().isEmpty() || "REVIEW".equals(reference.status())) {
            return 1;
        }
        return 2;
    }

    private Map<String, Number> safeMetrics(JsonNode node) {
        Map<String, Number> metrics = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return metrics;
        }
        node.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            JsonNode value = entry.getValue();
            if (!isSensitiveField(field) && value.isNumber() && isSafeMetricName(field)) {
                metrics.put(field, value.numberValue());
            }
        });
        return metrics;
    }

    private Map<String, Boolean> safeFlags(JsonNode node) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return flags;
        }
        node.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            JsonNode value = entry.getValue();
            String normalized = normalizeFieldName(field);
            if (!"pass".equals(normalized)
                    && !"passed".equals(normalized)
                    && !isSensitiveField(field)
                    && value.isBoolean()
                    && isSafeFlagName(field)) {
                flags.put(field, value.booleanValue());
            }
        });
        return flags;
    }

    private void mergeCheckSummaries(JsonNode checks,
                                     Map<String, Number> metrics,
                                     Map<String, Boolean> flags) {
        if (!checks.isArray()) {
            return;
        }
        metrics.put("checkCount", checks.size());
        if (checks.size() != 1 || !checks.get(0).isObject()) {
            return;
        }
        safeMetrics(checks.get(0)).forEach(metrics::putIfAbsent);
        safeFlags(checks.get(0)).forEach(flags::putIfAbsent);
    }

    private JsonNode firstDirectCaseArray(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String fieldName : CASE_ARRAY_FIELD_NAMES) {
            JsonNode cases = node.get(fieldName);
            if (cases != null && cases.isArray()) {
                return cases;
            }
        }
        return null;
    }

    private void mergeCaseSummaries(JsonNode cases, Map<String, Number> metrics) {
        if (cases == null || !cases.isArray()) {
            return;
        }
        int caseCount = 0;
        int knownOutcomeCount = 0;
        int passedCaseCount = 0;
        int failedCaseCount = 0;
        int ragTriggeredCaseCount = 0;
        int ragRequiredCaseCount = 0;
        int evidenceCaseCount = 0;
        int citationCaseCount = 0;
        for (JsonNode item : cases) {
            if (!item.isObject()) {
                continue;
            }
            caseCount++;
            Optional<Boolean> passed = optionalBoolean(item, "passed")
                    .or(() -> optionalBoolean(item, "pass"));
            if (passed.isPresent()) {
                knownOutcomeCount++;
                if (Boolean.TRUE.equals(passed.get())) {
                    passedCaseCount++;
                } else {
                    failedCaseCount++;
                }
            } else {
                String status = evalCaseStatus(item);
                if (isPassStatus(status)) {
                    knownOutcomeCount++;
                    passedCaseCount++;
                } else if (isFailedStatus(status)) {
                    knownOutcomeCount++;
                    failedCaseCount++;
                }
            }
            if (optionalBoolean(item, "ragTriggered").orElse(false)) {
                ragTriggeredCaseCount++;
            }
            if (optionalBoolean(item, "ragRequired").orElse(false)) {
                ragRequiredCaseCount++;
            }
            Integer evidenceCount = optionalInt(item, "evidenceCount");
            if (evidenceCount != null && evidenceCount > 0) {
                evidenceCaseCount++;
            }
            Integer citationCount = optionalInt(item, "citationCount");
            if (citationCount != null && citationCount > 0) {
                citationCaseCount++;
            }
        }
        metrics.putIfAbsent("caseCount", caseCount);
        if (knownOutcomeCount > 0) {
            metrics.putIfAbsent("passedCaseCount", passedCaseCount);
            metrics.putIfAbsent("failedCaseCount", failedCaseCount);
            metrics.putIfAbsent("casePassRate", (double) passedCaseCount / knownOutcomeCount);
        }
        metrics.putIfAbsent("ragTriggeredCaseCount", ragTriggeredCaseCount);
        metrics.putIfAbsent("ragRequiredCaseCount", ragRequiredCaseCount);
        metrics.putIfAbsent("evidenceCaseCount", evidenceCaseCount);
        metrics.putIfAbsent("citationCaseCount", citationCaseCount);
    }

    private List<String> gateFailureBuckets(JsonNode node) {
        LinkedHashSet<String> buckets = new LinkedHashSet<>(stringList(node.path("failureBuckets")));
        buckets.addAll(stringList(node.path("hardFailureBuckets")));
        return List.copyOf(buckets);
    }

    private QualityTokenUsageSummary extractTokenUsage(JsonNode root) {
        TokenUsageAccumulator accumulator = new TokenUsageAccumulator();
        collectTokenUsage(root, accumulator);
        return accumulator.toSummary();
    }

    private void collectTokenUsage(JsonNode node, TokenUsageAccumulator accumulator) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String field = entry.getKey();
                JsonNode value = entry.getValue();
                String normalized = normalizeFieldName(field);
                if (value.isNumber()) {
                    switch (normalized) {
                        case "prompttokens" -> accumulator.promptTokens = value.intValue();
                        case "completiontokens" -> accumulator.completionTokens = value.intValue();
                        case "totaltokens" -> accumulator.totalTokens = value.intValue();
                        case "estimatedcost" -> accumulator.estimatedCost = value.decimalValue();
                        default -> {
                        }
                    }
                } else if (isTokenUsageContainer(normalized)) {
                    collectTokenUsage(value, accumulator);
                } else if (!isSensitiveField(field) && value.isContainerNode()) {
                    collectTokenUsage(value, accumulator);
                }
            });
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                collectTokenUsage(item, accumulator);
            }
        }
    }

    private QualityRunDiagnostics buildDiagnostics(JsonNode root,
                                                   List<QualityGateSummary> gates,
                                                   List<QualityEvalCaseResultDetail> evalCases,
                                                   List<QualityTraceReference> traceReferences) {
        DocumentCoverageAccumulator documentCoverage = new DocumentCoverageAccumulator();
        ContextSourceAccumulator contextSources = new ContextSourceAccumulator();
        collectSafeObjectSummaries(root, documentCoverage, contextSources);
        int toolCallCount = sumMetric(gates, evalCases, "toolCallCount");
        int toolFailureCount = countBuckets(gates, evalCases, traceReferences, "tool");
        int toolArgsReviewCount = countBuckets(gates, evalCases, traceReferences, "tool", "arg", "param");
        int memoryMetricCount = sumMetric(gates, evalCases, "memoryCount");
        int memoryHitCount = Math.max(memoryMetricCount, contextSources.memoryHitCount);
        int memoryReviewCount = countBuckets(gates, evalCases, traceReferences, "memory");
        int memoryTriggerCount = Math.max(
                countPositiveMetric(gates, evalCases, "memoryCount"),
                countTrueFlag(gates, evalCases, "memoryTriggered", "memoryHit")
        );

        return new QualityRunDiagnostics(
                documentCoverage.toSummary(),
                new QualityRunDiagnostics.ToolQualitySummary(
                        nullableInt(toolCallCount),
                        nullableInt(toolFailureCount),
                        nullableInt(toolArgsReviewCount)
                ),
                new QualityRunDiagnostics.MemoryQualitySummary(
                        nullableInt(memoryTriggerCount),
                        nullableInt(memoryHitCount),
                        nullableInt(memoryReviewCount),
                        nullableInt(contextSources.ragEvidenceCount)
                ),
                parserQualitySummary(root.path("parserQualityReport"))
        );
    }

    private QualityRunDiagnostics.ParserQualitySummary parserQualitySummary(JsonNode node) {
        if (node == null || !node.isObject()) {
            return QualityRunDiagnostics.ParserQualitySummary.empty();
        }
        JsonNode fileTypeCoverage = node.path("fileTypeCoverage");
        JsonNode fixtureStructureCoverage = node.path("fixtureStructureCoverage");
        JsonNode parseStatusSummary = node.path("parseStatusSummary");
        JsonNode sourceLocatorSummary = node.path("sourceLocatorSummary");
        JsonNode ragChainSummary = node.path("ragChainSummary");
        JsonNode boundarySummary = node.path("boundarySummary");
        JsonNode warningsSummary = node.path("warningsSummary");
        return new QualityRunDiagnostics.ParserQualitySummary(
                arraySize(fileTypeCoverage.path("expectedTypes")),
                arraySize(fileTypeCoverage.path("coveredTypes")),
                arraySize(fileTypeCoverage.path("missingTypes")),
                optionalBoolean(fileTypeCoverage, "allCovered").orElse(null),
                arraySize(fixtureStructureCoverage.path("expectedSignals")),
                arraySize(fixtureStructureCoverage.path("coveredSignals")),
                arraySize(fixtureStructureCoverage.path("missingSignals")),
                optionalBoolean(fixtureStructureCoverage, "allCovered").orElse(null),
                optionalInt(parseStatusSummary, "fileCount"),
                optionalInt(parseStatusSummary, "parsedFileCount"),
                optionalInt(parseStatusSummary, "parserFailureCount"),
                optionalDouble(parseStatusSummary, "parsePassRate"),
                optionalInt(sourceLocatorSummary, "sourceLocatorCount"),
                optionalDouble(sourceLocatorSummary, "sourceLocatorCoverageRate"),
                optionalInt(ragChainSummary, "chunkCountKnown"),
                optionalInt(ragChainSummary, "chunkCount"),
                optionalInt(ragChainSummary, "retrieveHitCount"),
                optionalInt(ragChainSummary, "directRetrieveHitCount"),
                optionalInt(ragChainSummary, "qaRetrievalHitCount"),
                optionalInt(ragChainSummary, "citationCount"),
                optionalInt(ragChainSummary, "directRetrieveOkCount"),
                optionalInt(ragChainSummary, "qaRetrieveOkCount"),
                optionalInt(ragChainSummary, "directRetrieveNoEvidenceCount"),
                optionalInt(ragChainSummary, "qaRetrieveNoEvidenceCount"),
                optionalInt(ragChainSummary, "directRetrieveMaxAttempts"),
                optionalInt(ragChainSummary, "qaRetrieveMaxAttempts"),
                optionalBoolean(ragChainSummary, "environmentUnstable").orElse(null),
                optionalDouble(ragChainSummary, "retrieveCoverageRate"),
                optionalDouble(ragChainSummary, "citationCoverageRate"),
                optionalInt(boundarySummary, "negativeCaseCount"),
                optionalInt(boundarySummary, "negativeCasePassCount"),
                optionalInt(boundarySummary, "negativeCaseFailCount"),
                optionalDouble(boundarySummary, "boundaryPassRate"),
                optionalBoolean(boundarySummary, "unsupportedUploadRejected").orElse(null),
                optionalInt(warningsSummary, "warningCountKnown"),
                optionalInt(warningsSummary, "totalWarningCount"),
                optionalInt(warningsSummary, "filesWithWarnings"),
                stringList(node.path("reviewReasons")),
                stringList(node.path("unavailableMetrics"))
        );
    }

    private Integer arraySize(JsonNode node) {
        return node != null && node.isArray() ? node.size() : null;
    }

    private Integer optionalInt(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isNumber() && !isSensitiveField(fieldName) ? value.intValue() : null;
    }

    private Double optionalDouble(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isNumber() && !isSensitiveField(fieldName) ? value.doubleValue() : null;
    }

    private void collectSafeObjectSummaries(JsonNode node,
                                            DocumentCoverageAccumulator documentCoverage,
                                            ContextSourceAccumulator contextSources) {
        if (node == null || !node.isContainerNode()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String field = entry.getKey();
                JsonNode value = entry.getValue();
                if (isSensitiveField(field)) {
                    return;
                }
                String normalized = normalizeFieldName(field);
                if ("documenthitcounts".equals(normalized) && value.isObject()) {
                    value.fields().forEachRemaining(hitEntry -> {
                        if (hitEntry.getValue().isNumber()) {
                            documentCoverage.accept(hitEntry.getValue().intValue());
                        }
                    });
                    return;
                }
                if ("contextsourcecounts".equals(normalized) && value.isObject()) {
                    value.fields().forEachRemaining(sourceEntry -> {
                        if (sourceEntry.getValue().isNumber()) {
                            contextSources.accept(sourceEntry.getKey(), sourceEntry.getValue().intValue());
                        }
                    });
                    return;
                }
                if (value.isContainerNode() && !TOP_LEVEL_SKIP_FIELDS.contains(field)) {
                    collectSafeObjectSummaries(value, documentCoverage, contextSources);
                }
            });
            return;
        }
        for (JsonNode item : node) {
            collectSafeObjectSummaries(item, documentCoverage, contextSources);
        }
    }

    private int sumMetric(List<QualityGateSummary> gates,
                          List<QualityEvalCaseResultDetail> evalCases,
                          String metricName) {
        String expected = normalizeFieldName(metricName);
        int sum = 0;
        for (QualityGateSummary gate : gates) {
            sum += sumMetricValues(gate.metrics(), expected);
        }
        for (QualityEvalCaseResultDetail evalCase : evalCases) {
            sum += sumMetricValues(evalCase.metrics(), expected);
        }
        return sum;
    }

    private int sumMetricValues(Map<String, Number> metrics, String expected) {
        int sum = 0;
        for (Map.Entry<String, Number> entry : metrics.entrySet()) {
            if (expected.equals(normalizeFieldName(entry.getKey()))) {
                sum += entry.getValue().intValue();
            }
        }
        return sum;
    }

    private int countPositiveMetric(List<QualityGateSummary> gates,
                                    List<QualityEvalCaseResultDetail> evalCases,
                                    String metricName) {
        String expected = normalizeFieldName(metricName);
        int count = 0;
        for (QualityGateSummary gate : gates) {
            count += countPositiveValues(gate.metrics(), expected);
        }
        for (QualityEvalCaseResultDetail evalCase : evalCases) {
            count += countPositiveValues(evalCase.metrics(), expected);
        }
        return count;
    }

    private int countPositiveValues(Map<String, Number> metrics, String expected) {
        int count = 0;
        for (Map.Entry<String, Number> entry : metrics.entrySet()) {
            if (expected.equals(normalizeFieldName(entry.getKey())) && entry.getValue().doubleValue() > 0) {
                count++;
            }
        }
        return count;
    }

    private int countTrueFlag(List<QualityGateSummary> gates,
                              List<QualityEvalCaseResultDetail> evalCases,
                              String... flagNames) {
        Set<String> expected = new LinkedHashSet<>();
        for (String flagName : flagNames) {
            expected.add(normalizeFieldName(flagName));
        }
        int count = 0;
        for (QualityGateSummary gate : gates) {
            count += countTrueFlags(gate.flags(), expected);
        }
        for (QualityEvalCaseResultDetail evalCase : evalCases) {
            count += countTrueFlags(evalCase.flags(), expected);
        }
        return count;
    }

    private int countTrueFlags(Map<String, Boolean> flags, Set<String> expected) {
        int count = 0;
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue()) && expected.contains(normalizeFieldName(entry.getKey()))) {
                count++;
            }
        }
        return count;
    }

    private int countBuckets(List<QualityGateSummary> gates,
                             List<QualityEvalCaseResultDetail> evalCases,
                             List<QualityTraceReference> traceReferences,
                             String required,
                             String... optionalAny) {
        int count = 0;
        for (QualityGateSummary gate : gates) {
            count += countMatchingBuckets(gate.failureBuckets(), required, optionalAny);
            count += countMatchingBuckets(gate.reviewBuckets(), required, optionalAny);
        }
        for (QualityEvalCaseResultDetail evalCase : evalCases) {
            count += countMatchingBuckets(evalCase.failureBuckets(), required, optionalAny);
            count += countMatchingBuckets(evalCase.reviewBuckets(), required, optionalAny);
        }
        for (QualityTraceReference traceReference : traceReferences) {
            count += countMatchingBuckets(traceReference.failureBuckets(), required, optionalAny);
            count += countMatchingBuckets(traceReference.reviewBuckets(), required, optionalAny);
        }
        return count;
    }

    private int countMatchingBuckets(List<String> buckets, String required, String... optionalAny) {
        int count = 0;
        for (String bucket : buckets) {
            String normalized = normalizeFieldName(bucket);
            if (!normalized.contains(normalizeFieldName(required))) {
                continue;
            }
            if (optionalAny.length == 0) {
                count++;
                continue;
            }
            for (String optional : optionalAny) {
                if (normalized.contains(normalizeFieldName(optional))) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private Integer nullableInt(int value) {
        return value == 0 ? null : value;
    }

    private List<String> mergeBuckets(JsonNode root, List<QualityGateSummary> gates, String fieldName) {
        LinkedHashSet<String> buckets = new LinkedHashSet<>(stringList(root.path(fieldName)));
        if ("failureBuckets".equals(fieldName)) {
            buckets.addAll(stringList(root.path("hardFailureBuckets")));
        }
        for (QualityGateSummary gate : gates) {
            if ("failureBuckets".equals(fieldName)) {
                buckets.addAll(gate.failureBuckets());
            } else if ("reviewBuckets".equals(fieldName)) {
                buckets.addAll(gate.reviewBuckets());
            }
        }
        return List.copyOf(buckets);
    }

    private JsonNode findFirstArray(JsonNode root, String... fieldNames) {
        List<JsonNode> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            JsonNode node = stack.remove(stack.size() - 1);
            if (!node.isContainerNode()) {
                continue;
            }
            if (node.isObject()) {
                for (String fieldName : fieldNames) {
                    JsonNode child = node.get(fieldName);
                    if (child != null && child.isArray()) {
                        return child;
                    }
                }
                node.fields().forEachRemaining(entry -> {
                    if (!isSensitiveField(entry.getKey()) && entry.getValue().isContainerNode()) {
                        stack.add(entry.getValue());
                    }
                });
            } else if (node.isArray()) {
                for (JsonNode child : node) {
                    if (child.isContainerNode()) {
                        stack.add(child);
                    }
                }
            }
        }
        return null;
    }

    private String inferStatus(List<QualityGateSummary> gates, List<String> failureBuckets, List<String> reviewBuckets) {
        if (!failureBuckets.isEmpty()) {
            return "FAILED_CORE_FLOW";
        }
        if (!reviewBuckets.isEmpty()) {
            return "REVIEW";
        }
        if (!gates.isEmpty() && gates.stream().allMatch(gate -> Boolean.TRUE.equals(gate.passed()))) {
            return "PASS";
        }
        return "REVIEW";
    }

    private boolean hasFailures(QualityGateSummary gate) {
        return !gate.failureBuckets().isEmpty() || Boolean.FALSE.equals(gate.passed());
    }

    private Optional<String> firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isTextual() && !value.asText().isBlank() && !isSensitiveField(fieldName)) {
                return Optional.of(value.asText());
            }
        }
        return Optional.empty();
    }

    private String evalCaseStatus(JsonNode item) {
        Optional<String> explicitStatus = firstText(item, "status").map(this::normalizeStatus);
        if (explicitStatus.isPresent()) {
            return explicitStatus.get();
        }
        Optional<Boolean> passed = optionalBoolean(item, "passed")
                .or(() -> optionalBoolean(item, "pass"));
        if (passed.isPresent()) {
            return Boolean.TRUE.equals(passed.get()) ? "PASS" : "FAILED";
        }
        return "";
    }

    private boolean isPassStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = normalizeStatus(status);
        return "PASS".equals(normalized) || "SUCCESS".equals(normalized) || "OK".equals(normalized);
    }

    private boolean isFailedStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = normalizeStatus(status);
        return normalized.startsWith("FAILED") || "FAIL".equals(normalized) || "ERROR".equals(normalized);
    }

    private Optional<String> firstScalarText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (isSensitiveField(fieldName) || value == null || value.isMissingNode() || value.isNull()) {
                continue;
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return Optional.of(value.asText());
            }
            if (value.isNumber() || value.isBoolean()) {
                return Optional.of(value.asText());
            }
        }
        return Optional.empty();
    }

    private Optional<Boolean> optionalBoolean(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isBoolean()) {
            return Optional.of(value.booleanValue());
        }
        return Optional.empty();
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && isSafeBucketValue(item.asText())) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private boolean isSafeBucketValue(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return !lower.contains("bearer ")
                && !lower.contains("sk-")
                && !lower.contains("jdbc:")
                && !lower.contains("http://")
                && !lower.contains("https://")
                && !lower.contains("password")
                && !lower.contains("secret")
                && !lower.contains("api_key")
                && !lower.contains("apikey");
    }

    private boolean isSafeMetricName(String field) {
        String normalized = normalizeFieldName(field);
        return normalized.endsWith("count")
                || normalized.endsWith("rate")
                || normalized.endsWith("score")
                || normalized.endsWith("tokens")
                || normalized.endsWith("ms")
                || normalized.endsWith("hits")
                || normalized.endsWith("citations")
                || normalized.equals("topk")
                || normalized.equals("estimatedcost")
                || normalized.equals("latencyms")
                || normalized.equals("durationms")
                || normalized.equals("modelcallcount")
                || normalized.equals("casepassrate");
    }

    private boolean isSafeFlagName(String field) {
        String normalized = normalizeFieldName(field);
        return normalized.endsWith("enabled")
                || normalized.endsWith("applied")
                || normalized.endsWith("passed")
                || normalized.endsWith("pass")
                || normalized.endsWith("hit")
                || normalized.endsWith("triggered")
                || normalized.endsWith("visible")
                || normalized.endsWith("stored")
                || normalized.endsWith("present")
                || normalized.endsWith("covered")
                || normalized.endsWith("correct")
                || normalized.endsWith("supported")
                || normalized.endsWith("required")
                || normalized.endsWith("rejected")
                || normalized.endsWith("handled")
                || normalized.startsWith("covers")
                || normalized.contains("covers")
                || normalized.equals("llmcalled")
                || normalized.equals("modelskipped")
                || normalized.equals("success")
                || normalized.equals("noevidence")
                || normalized.equals("ragtriggered")
                || normalized.equals("ragrequired");
    }

    private boolean isSensitiveField(String field) {
        String normalized = normalizeFieldName(field);
        return normalized.equals("prompt")
                || normalized.contains("systemprompt")
                || normalized.contains("prompttext")
                || normalized.contains("rawprompt")
                || normalized.equals("answer")
                || normalized.contains("answertext")
                || normalized.contains("answercontent")
                || normalized.contains("rawanswer")
                || normalized.contains("finalanswer")
                || normalized.contains("content")
                || normalized.contains("documenttext")
                || normalized.contains("documentfulltext")
                || normalized.contains("evidencecontext")
                || normalized.contains("contexttext")
                || normalized.contains("apikey")
                || normalized.contains("accesstoken")
                || normalized.contains("tokenvalue")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("connectionstring")
                || normalized.contains("cloudaddress")
                || normalized.contains("cloudhost")
                || normalized.equals("url")
                || normalized.equals("uri")
                || normalized.equals("endpoint");
    }

    private boolean isTokenUsageContainer(String normalizedField) {
        return "usage".equals(normalizedField)
                || "tokenusage".equals(normalizedField)
                || "tokenusagesummary".equals(normalizedField);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "REVIEW";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeFieldName(String field) {
        return field == null ? "" : field.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String rootGateName(JsonNode root) {
        String marker = firstText(root, "smokeMarker", "marker", "runMarker").orElse("");
        if (normalizeFieldName(marker).contains("conversationgrounding")) {
            return "conversationGrounding";
        }
        return "caseSummary";
    }

    private String markerFromPath(QualityArtifactFile file) {
        Path parent = file.path().getParent();
        if (parent != null && parent.getFileName() != null) {
            return parent.getFileName().toString();
        }
        return Objects.toString(file.relativePath(), "unknown-quality-run");
    }

    private FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException ignored) {
            return FileTime.from(Instant.EPOCH);
        }
    }

    private static Path resolveRepoRoot() {
        return normalizeRepoRoot(Path.of(""));
    }

    static Path normalizeRepoRoot(Path startPath) {
        Path candidate = startPath == null ? Path.of("") : startPath;
        Path cwd = candidate.toAbsolutePath().normalize();
        Path current = cwd;
        while (current != null) {
            if (Files.isDirectory(current.resolve("backend"))
                    && Files.isDirectory(current.resolve("frontend"))
                    && Files.isDirectory(current.resolve("docs"))) {
                return current;
            }
            if (current.getFileName() != null && "backend".equals(current.getFileName().toString())) {
                Path parent = current.getParent();
                if (parent != null) {
                    return parent;
                }
            }
            current = current.getParent();
        }
        return cwd;
    }

    private record QualityArtifactFile(
            String sourceRoot,
            String relativePath,
            Path path,
            FileTime modifiedTime
    ) {
        private Instant updatedAt() {
            return modifiedTime.toInstant();
        }
    }

    private record ExtractedEvalCase(JsonNode node, String gateName) {
    }

    private static final class DocumentCoverageAccumulator {
        private int documentCount;
        private int coveredDocumentCount;
        private int zeroHitDocumentCount;
        private Integer maxHitsPerDocument;
        private Integer minHitsPerDocument;

        private void accept(int hits) {
            documentCount++;
            if (hits > 0) {
                coveredDocumentCount++;
            } else {
                zeroHitDocumentCount++;
            }
            maxHitsPerDocument = maxHitsPerDocument == null ? hits : Math.max(maxHitsPerDocument, hits);
            minHitsPerDocument = minHitsPerDocument == null ? hits : Math.min(minHitsPerDocument, hits);
        }

        private QualityRunDiagnostics.DocumentCoverageSummary toSummary() {
            if (documentCount == 0) {
                return QualityRunDiagnostics.DocumentCoverageSummary.empty();
            }
            return new QualityRunDiagnostics.DocumentCoverageSummary(
                    documentCount,
                    coveredDocumentCount,
                    zeroHitDocumentCount,
                    maxHitsPerDocument,
                    minHitsPerDocument
            );
        }
    }

    private final class ContextSourceAccumulator {
        private int memoryHitCount;
        private int ragEvidenceCount;

        private void accept(String source, int count) {
            String normalized = normalizeFieldName(source);
            if (normalized.contains("memory")) {
                memoryHitCount += count;
            }
            if (normalized.contains("rag") || normalized.contains("evidence")) {
                ragEvidenceCount += count;
            }
        }
    }

    private static final class CaseTrendAccumulator {
        private final Map<String, CaseCounts> counts = new LinkedHashMap<>();

        private void accept(QualityRunDetail detail) {
            String marker = detail.summary().marker();
            for (QualityEvalCaseResultDetail item : detail.evalCases()) {
                boolean failed = item.status().startsWith("FAILED") || !item.failureBuckets().isEmpty();
                boolean review = "REVIEW".equals(item.status()) || !item.reviewBuckets().isEmpty();
                if (!failed && !review) {
                    continue;
                }
                counts.computeIfAbsent(item.caseId(), ignored -> new CaseCounts())
                        .accept(failed, review, item.status(), marker);
            }
        }

        private List<QualityRepeatedCaseSummary> repeatedCases() {
            return counts.entrySet().stream()
                    .filter(entry -> entry.getValue().failedCount + entry.getValue().reviewCount >= 2)
                    .sorted((left, right) -> Integer.compare(
                            right.getValue().failedCount + right.getValue().reviewCount,
                            left.getValue().failedCount + left.getValue().reviewCount
                    ))
                    .limit(10)
                    .map(entry -> {
                        CaseCounts value = entry.getValue();
                        return new QualityRepeatedCaseSummary(
                                entry.getKey(),
                                value.failedCount,
                                value.reviewCount,
                                value.latestStatus,
                                value.latestRunMarker
                        );
                    })
                    .toList();
        }
    }

    private static final class CaseCounts {
        private int failedCount;
        private int reviewCount;
        private String latestStatus = "";
        private String latestRunMarker = "";

        private void accept(boolean failed, boolean review, String status, String marker) {
            if (failed) {
                failedCount++;
            }
            if (review) {
                reviewCount++;
            }
            if (latestRunMarker.isBlank()) {
                latestStatus = status == null ? "" : status;
                latestRunMarker = marker == null ? "" : marker;
            }
        }
    }

    private static final class TokenUsageAccumulator {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private BigDecimal estimatedCost;

        private QualityTokenUsageSummary toSummary() {
            return new QualityTokenUsageSummary(promptTokens, completionTokens, totalTokens, estimatedCost);
        }
    }
}
