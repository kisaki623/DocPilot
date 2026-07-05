package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.service.QualityArtifactService;
import com.docpilot.backend.quality.vo.QualityEvalCaseResultDetail;
import com.docpilot.backend.quality.vo.QualityGateSummary;
import com.docpilot.backend.quality.vo.QualityRunDetail;
import com.docpilot.backend.quality.vo.QualityRunSummary;
import com.docpilot.backend.quality.vo.QualityTokenUsageSummary;
import com.docpilot.backend.quality.vo.QualityTraceReference;
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
            "backend/target/memory-quality",
            "backend/target/memory-provider",
            "backend/target/agent-quality-eval",
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

    private final Path repoRoot;
    private final ObjectMapper objectMapper;

    @Autowired
    public QualityArtifactServiceImpl(ObjectMapper objectMapper) {
        this(resolveRepoRoot(), objectMapper);
    }

    QualityArtifactServiceImpl(Path repoRoot, ObjectMapper objectMapper) {
        this.repoRoot = repoRoot == null ? resolveRepoRoot() : repoRoot.toAbsolutePath().normalize();
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
        List<QualityGateSummary> gates = extractGates(root);
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
        return new QualityRunDetail(summary, gates, evalCases, toTraceReferences(extractedEvalCases));
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
            for (String fieldName : List.of("caseResults", "caseEvaluations", "evalCases")) {
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
            results.add(new QualityEvalCaseResultDetail(
                    caseId,
                    firstText(item, "caseType", "category").orElse(null),
                    firstText(item, "status").map(this::normalizeStatus).orElse(null),
                    optionalBoolean(item, "passed").orElse(null),
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
            String status = firstText(item, "status").map(this::normalizeStatus).orElse("");
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
                    firstText(item, "caseType", "category").orElse(null),
                    status,
                    extracted.gateName(),
                    traceId,
                    agentRunId,
                    firstScalarText(item, "conversationId").orElse(null),
                    failureBuckets,
                    reviewBuckets
            ));
        }
        return references.stream()
                .sorted(Comparator.comparingInt(this::traceReferencePriority))
                .toList();
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
                || normalized.endsWith("hits")
                || normalized.endsWith("citations")
                || normalized.equals("topk")
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
                || normalized.endsWith("covered")
                || normalized.endsWith("correct")
                || normalized.endsWith("supported")
                || normalized.endsWith("required")
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
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (cwd.getFileName() != null && "backend".equals(cwd.getFileName().toString())) {
            return cwd.getParent();
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
