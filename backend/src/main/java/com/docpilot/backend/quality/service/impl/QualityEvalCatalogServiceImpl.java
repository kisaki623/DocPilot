package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.service.QualityArtifactService;
import com.docpilot.backend.quality.service.QualityEvalCatalogService;
import com.docpilot.backend.quality.vo.QualityEvalCaseCatalogItem;
import com.docpilot.backend.quality.vo.QualityEvalCaseResultDetail;
import com.docpilot.backend.quality.vo.QualityRunDetail;
import com.docpilot.backend.quality.vo.QualityRunSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class QualityEvalCatalogServiceImpl implements QualityEvalCatalogService {

    private static final String DEFAULT_CASE_FILE = "backend/src/test/resources/quality/agent-quality-eval-cases.json";
    private static final int DEFAULT_STATUS_SCAN_LIMIT = 20;

    private final Path repoRoot;
    private final ObjectMapper objectMapper;
    private final QualityArtifactService qualityArtifactService;

    @Autowired
    public QualityEvalCatalogServiceImpl(ObjectMapper objectMapper, QualityArtifactService qualityArtifactService) {
        this(resolveRepoRoot(), objectMapper, qualityArtifactService);
    }

    QualityEvalCatalogServiceImpl(
            Path repoRoot,
            ObjectMapper objectMapper,
            QualityArtifactService qualityArtifactService) {
        this.repoRoot = repoRoot == null ? resolveRepoRoot() : repoRoot.toAbsolutePath().normalize();
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.qualityArtifactService = qualityArtifactService;
    }

    @Override
    public List<QualityEvalCaseCatalogItem> listEvalCases() {
        List<CatalogCaseDefinition> definitions = loadDefinitions();
        List<QualityRunDetail> recentDetails = loadRecentDetails();
        return definitions.stream()
                .map(definition -> toCatalogItem(definition, recentDetails))
                .sorted(Comparator.comparing(QualityEvalCaseCatalogItem::caseId))
                .toList();
    }

    private List<CatalogCaseDefinition> loadDefinitions() {
        Path caseFile = repoRoot.resolve(DEFAULT_CASE_FILE).normalize();
        if (!Files.isRegularFile(caseFile)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(caseFile.toFile());
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<CatalogCaseDefinition> definitions = new ArrayList<>();
            for (JsonNode item : root) {
                if (item != null && item.isObject()) {
                    firstSafeText(item, "caseId").ifPresent(caseId -> definitions.add(new CatalogCaseDefinition(
                            caseId,
                            safeVersion(item.path("caseVersion")),
                            firstSafeText(item, "owner").orElse(""),
                            firstSafeText(item, "lastUpdated").orElse(""),
                            firstSafeText(item, "riskLevel").orElse(""),
                            safeList(item.path("sourceIssueIds")),
                            firstSafeText(item, "lastVerifiedMarker").orElse(""),
                            safeList(item.path("remediationHints")),
                            safeList(item.path("tags")),
                            safeList(item.path("expectedEvidence")),
                            safeList(item.path("expectedTools")),
                            safeRuleNames(item.path("scoringRules"))
                    )));
                }
            }
            return definitions;
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private List<QualityRunDetail> loadRecentDetails() {
        if (qualityArtifactService == null) {
            return List.of();
        }
        return qualityArtifactService.listRecentRuns(DEFAULT_STATUS_SCAN_LIMIT).stream()
                .map(QualityRunSummary::marker)
                .map(qualityArtifactService::getRunDetail)
                .flatMap(Optional::stream)
                .toList();
    }

    private QualityEvalCaseCatalogItem toCatalogItem(
            CatalogCaseDefinition definition,
            List<QualityRunDetail> recentDetails) {
        Optional<LatestCaseResult> latest = findLatestResult(definition.caseId(), recentDetails);
        return new QualityEvalCaseCatalogItem(
                definition.caseId(),
                definition.caseVersion(),
                definition.owner(),
                definition.lastUpdated(),
                definition.riskLevel(),
                definition.sourceIssueIds(),
                definition.lastVerifiedMarker(),
                definition.remediationHints(),
                definition.tags().isEmpty() ? "agent_quality" : definition.tags().get(0),
                definition.tags(),
                definition.expectedEvidence(),
                definition.expectedTools(),
                definition.scoringRules(),
                latest.map(result -> result.caseResult().status()).orElse("NOT_RUN"),
                latest.map(result -> result.runSummary().marker()).orElse(""),
                latest.map(result -> result.caseResult().traceId()).orElse(""),
                latest.map(result -> result.caseResult().agentRunId()).orElse(""),
                latest.map(result -> result.caseResult().failureBuckets()).orElse(List.of()),
                latest.map(result -> result.caseResult().reviewBuckets()).orElse(List.of())
        );
    }

    private Optional<LatestCaseResult> findLatestResult(String caseId, List<QualityRunDetail> recentDetails) {
        for (QualityRunDetail detail : recentDetails) {
            for (QualityEvalCaseResultDetail item : detail.evalCases()) {
                if (caseId.equals(item.caseId())) {
                    return Optional.of(new LatestCaseResult(detail.summary(), item));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> firstSafeText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (!value.isTextual()) {
            return Optional.empty();
        }
        String text = value.asText("").trim();
        return isSafeIdentifier(text) ? Optional.of(text) : Optional.empty();
    }

    private List<String> safeList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                String value = item.asText("").trim();
                if (isSafeIdentifier(value)) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private List<String> safeRuleNames(JsonNode node) {
        if (!node.isObject()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        node.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            if (isSafeIdentifier(field)) {
                JsonNode value = entry.getValue();
                if (value.isBoolean()) {
                    values.add(field + "=" + value.booleanValue());
                } else if (value.isNumber()) {
                    values.add(field + "=" + value.numberValue());
                } else {
                    values.add(field);
                }
            }
        });
        return List.copyOf(values);
    }

    private int safeVersion(JsonNode node) {
        if (!node.isInt()) {
            return 0;
        }
        int value = node.asInt(0);
        return value < 0 || value > 9999 ? 0 : value;
    }

    private boolean isSafeIdentifier(String value) {
        if (value == null || value.isBlank() || value.length() > 80) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("bearer")
                || lower.contains("sk-")
                || lower.contains("password")
                || lower.contains("secret")
                || lower.contains("apikey")
                || lower.contains("api_key")
                || lower.contains("token")
                || lower.contains("jdbc:")
                || lower.contains("http://")
                || lower.contains("https://")) {
            return false;
        }
        return value.matches("[A-Za-z0-9_.:=/-]+");
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (cwd.getFileName() != null && "backend".equals(cwd.getFileName().toString())) {
            return cwd.getParent();
        }
        return cwd;
    }

    private record CatalogCaseDefinition(
            String caseId,
            int caseVersion,
            String owner,
            String lastUpdated,
            String riskLevel,
            List<String> sourceIssueIds,
            String lastVerifiedMarker,
            List<String> remediationHints,
            List<String> tags,
            List<String> expectedEvidence,
            List<String> expectedTools,
            List<String> scoringRules
    ) {
    }

    private record LatestCaseResult(
            QualityRunSummary runSummary,
            QualityEvalCaseResultDetail caseResult
    ) {
    }
}
