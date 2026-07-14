package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.entity.QualityImportEvent;
import com.docpilot.backend.quality.entity.QualityRun;
import com.docpilot.backend.quality.entity.QualityRunCase;
import com.docpilot.backend.quality.entity.QualityRunGate;
import com.docpilot.backend.quality.mapper.QualityImportEventMapper;
import com.docpilot.backend.quality.mapper.QualityRunCaseMapper;
import com.docpilot.backend.quality.mapper.QualityRunGateMapper;
import com.docpilot.backend.quality.mapper.QualityRunMapper;
import com.docpilot.backend.quality.service.QualityArtifactImportService;
import com.docpilot.backend.quality.vo.QualityEvalCaseResultDetail;
import com.docpilot.backend.quality.vo.QualityGateSummary;
import com.docpilot.backend.quality.vo.QualityImportResult;
import com.docpilot.backend.quality.vo.QualityRunDetail;
import com.docpilot.backend.quality.vo.QualityRunSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class QualityArtifactImportServiceImpl implements QualityArtifactImportService {

    private static final List<String> ALLOWED_ROOTS = List.of(
            "backend/target/audit",
            "backend/target/rag-natural-corpus",
            "backend/target/rag-real-qa",
            "backend/target/rag-quality",
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
    private static final String INTERNAL_IMPORT_TEST_MARKER_PREFIX = "docpilot-import-";
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(?i)Authorization\\s*[:=]\\s*Bearer\\s+[^\\s\\\"'<>]{8,}"),
            Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{16,}"),
            Pattern.compile("(?i)\\bsk-[A-Za-z0-9_-]{12,}"),
            Pattern.compile("(?i)api[_-]?key\\s*[:=]\\s*[\\\"']?[^\\\"',\\s<>]{8,}"),
            Pattern.compile("(?i)(password|secret)\\s*[:=]\\s*[\\\"']?[^\\\"',\\s<>]{8,}"),
            Pattern.compile("(?i)jdbc:mysql:[^\\s\\\"'<>]+"),
            Pattern.compile("(?i)(mysql|postgres|redis)://[^\\s\\\"'<>]+"),
            Pattern.compile("(?i)https?://(?!127\\.0\\.0\\.1|localhost)[^\\s\\\"'<>]+"),
            Pattern.compile("(?i)BEGIN\\s+(RSA\\s+|EC\\s+|OPENSSH\\s+)?PRIVATE\\s+KEY")
    );

    private final Path repoRoot;
    private final QualityArtifactServiceImpl artifactScanner;
    private final QualityRunMapper qualityRunMapper;
    private final QualityRunGateMapper qualityRunGateMapper;
    private final QualityRunCaseMapper qualityRunCaseMapper;
    private final QualityImportEventMapper qualityImportEventMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String environment;
    private final long maxFileBytes;

    @Autowired
    public QualityArtifactImportServiceImpl(
            QualityArtifactServiceImpl artifactScanner,
            QualityRunMapper qualityRunMapper,
            QualityRunGateMapper qualityRunGateMapper,
            QualityRunCaseMapper qualityRunCaseMapper,
            QualityImportEventMapper qualityImportEventMapper,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.quality.console.environment:${spring.profiles.active:local}}") String environment,
            @Value("${app.quality.import.max-file-bytes:2097152}") long maxFileBytes) {
        this(resolveRepoRoot(),
                artifactScanner,
                qualityRunMapper,
                qualityRunGateMapper,
                qualityRunCaseMapper,
                qualityImportEventMapper,
                objectMapper,
                transactionManager,
                environment,
                maxFileBytes);
    }

    QualityArtifactImportServiceImpl(
            Path repoRoot,
            QualityArtifactServiceImpl artifactScanner,
            QualityRunMapper qualityRunMapper,
            QualityRunGateMapper qualityRunGateMapper,
            QualityRunCaseMapper qualityRunCaseMapper,
            QualityImportEventMapper qualityImportEventMapper,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            String environment,
            long maxFileBytes) {
        this.repoRoot = repoRoot == null ? resolveRepoRoot() : repoRoot.toAbsolutePath().normalize();
        this.artifactScanner = artifactScanner;
        this.qualityRunMapper = qualityRunMapper;
        this.qualityRunGateMapper = qualityRunGateMapper;
        this.qualityRunCaseMapper = qualityRunCaseMapper;
        this.qualityImportEventMapper = qualityImportEventMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.environment = environment == null || environment.isBlank() ? "local" : environment.trim();
        this.maxFileBytes = maxFileBytes <= 0 ? 2_097_152L : maxFileBytes;
    }

    @Override
    public QualityImportResult importRecentArtifacts(int limit, Long requestedByUserId) {
        int resolvedLimit = limit <= 0 ? DEFAULT_IMPORT_LIMIT : Math.min(limit, 500);
        int scanLimit = Math.min(500, Math.max(resolvedLimit, resolvedLimit * 5));
        ImportCounter counter = new ImportCounter();
        List<QualityRunSummary> summaries = artifactScanner.listRecentRuns(scanLimit).stream()
                .filter(summary -> summary != null && !isInternalImportTestMarker(summary.marker()))
                .limit(resolvedLimit)
                .toList();
        counter.scanned = summaries.size();
        for (QualityRunSummary summary : summaries) {
            importOne(summary, requestedByUserId, counter);
        }
        return counter.toResult();
    }

    private void importOne(QualityRunSummary summary, Long requestedByUserId, ImportCounter counter) {
        ImportCandidate candidate = null;
        try {
            if (summary == null) {
                throw new RejectedArtifactException("quality artifact summary is incomplete");
            }
            if (isInternalImportTestMarker(summary.marker())) {
                recordEvent(null, null, summary.marker(), "REJECTED",
                        "quality artifact import test marker is ignored", requestedByUserId);
                counter.rejected++;
                return;
            }
            candidate = resolveCandidate(summary);
            String raw = readRaw(candidate.path());
            assertRedacted(raw);
            String sha256 = sha256(raw);
            QualityRun existingByHash = qualityRunMapper.selectBySourceSha256(sha256);
            if (existingByHash != null) {
                recordEvent(candidate, sha256, summary.marker(), "SKIPPED_DUPLICATE",
                        "artifact already imported", requestedByUserId);
                counter.skippedDuplicate++;
                return;
            }
            QualityRunDetail detail = artifactScanner.getRunDetail(summary.marker())
                    .orElseThrow(() -> new IllegalArgumentException("quality artifact detail missing"));
            validateDetail(detail);
            ImportCandidate resolvedCandidate = candidate;
            QualityRunDetail snapshot = detail;
            String digest = sha256;
            Long requestedBy = requestedByUserId;
            ImportOutcome outcome = transactionTemplate.execute(status ->
                    persistSnapshot(snapshot, resolvedCandidate, digest, requestedBy));
            if (outcome == ImportOutcome.IMPORTED) {
                counter.imported++;
            } else if (outcome == ImportOutcome.UPDATED) {
                counter.updated++;
            } else if (outcome == ImportOutcome.REJECTED) {
                counter.rejected++;
            } else {
                counter.skippedDuplicate++;
            }
        } catch (RejectedArtifactException ex) {
            recordEvent(candidate, null, summary == null ? null : summary.marker(), "REJECTED",
                    ex.getMessage(), requestedByUserId);
            counter.rejected++;
        } catch (Exception ex) {
            recordEvent(candidate, null, summary == null ? null : summary.marker(), "FAILED",
                    "quality artifact import failed", requestedByUserId);
            counter.failed++;
        }
    }

    private ImportCandidate resolveCandidate(QualityRunSummary summary) throws IOException {
        if (summary == null || summary.source() == null || summary.artifactName() == null) {
            throw new RejectedArtifactException("quality artifact summary is incomplete");
        }
        String source = summary.source().trim().replace('\\', '/');
        if (!ALLOWED_ROOTS.contains(source)) {
            throw new RejectedArtifactException("quality artifact root is not whitelisted");
        }
        Path rootPath = repoRoot.resolve(source).normalize();
        Path artifactPath = rootPath.resolve(summary.artifactName()).normalize();
        if (!isAllowedFile(source, rootPath, artifactPath)) {
            throw new RejectedArtifactException("quality artifact file name is not whitelisted");
        }
        if (!Files.isRegularFile(artifactPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new RejectedArtifactException("quality artifact is not a regular file");
        }
        if (Files.isSymbolicLink(artifactPath)) {
            throw new RejectedArtifactException("quality artifact symlink is not allowed");
        }
        Path realRoot = rootPath.toRealPath();
        Path realArtifact = artifactPath.toRealPath();
        if (!realArtifact.startsWith(realRoot)) {
            throw new RejectedArtifactException("quality artifact path escapes whitelisted root");
        }
        return new ImportCandidate(source, rootPath.relativize(artifactPath).toString().replace('\\', '/'), artifactPath);
    }

    private boolean isAllowedFile(String root, Path rootPath, Path artifactPath) {
        String fileName = artifactPath.getFileName().toString();
        if (ARTIFACT_FILE_NAMES.contains(fileName)) {
            return true;
        }
        if ("backend/target/rag-quality".equals(root) && "latest-summary.json".equals(fileName)) {
            String relative = rootPath.relativize(artifactPath).toString().replace('\\', '/');
            return "rerank-representative-eval/latest-summary.json".equals(relative);
        }
        return false;
    }

    private String readRaw(Path path) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > maxFileBytes) {
            throw new RejectedArtifactException("quality artifact size is outside allowed range");
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private void assertRedacted(String raw) {
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(raw).find()) {
                throw new RejectedArtifactException("quality artifact redaction scan failed");
            }
        }
    }

    private void validateDetail(QualityRunDetail detail) {
        if (detail == null || detail.summary() == null) {
            throw new RejectedArtifactException("quality artifact schema is incomplete");
        }
        QualityRunSummary summary = detail.summary();
        if (summary.marker() == null || summary.marker().isBlank()) {
            throw new RejectedArtifactException("quality artifact marker is empty");
        }
        if (summary.artifactParseFailed()) {
            throw new RejectedArtifactException("quality artifact parse failed");
        }
    }

    private ImportOutcome persistSnapshot(QualityRunDetail detail,
                                          ImportCandidate candidate,
                                          String sha256,
                                          Long requestedByUserId) {
        QualityRun existingByMarker = qualityRunMapper.selectByMarker(detail.summary().marker());
        if (existingByMarker == null) {
            QualityRun run = toRunEntity(detail, candidate, sha256, 1);
            qualityRunMapper.insert(run);
            replaceChildren(run.getId(), detail);
            recordEvent(candidate, sha256, detail.summary().marker(), "IMPORTED",
                    "quality run imported", requestedByUserId);
            return ImportOutcome.IMPORTED;
        }
        if (!candidate.sourceRoot().equals(existingByMarker.getSourceRootKey())
                || !candidate.relativePath().equals(existingByMarker.getSourceRelativePath())) {
            recordEvent(candidate, sha256, detail.summary().marker(), "REJECTED",
                    "quality run marker conflicts with another source", requestedByUserId);
            return ImportOutcome.REJECTED;
        }
        QualityRun run = toRunEntity(detail, candidate, sha256,
                (existingByMarker.getImportRevision() == null ? 1 : existingByMarker.getImportRevision()) + 1);
        run.setId(existingByMarker.getId());
        qualityRunMapper.updateById(run);
        replaceChildren(existingByMarker.getId(), detail);
        recordEvent(candidate, sha256, detail.summary().marker(), "UPDATED",
                "quality run updated", requestedByUserId);
        return ImportOutcome.UPDATED;
    }

    private QualityRun toRunEntity(QualityRunDetail detail, ImportCandidate candidate, String sha256, int revision) {
        QualityRunSummary summary = detail.summary();
        QualityRun run = new QualityRun();
        run.setMarker(summary.marker());
        run.setStatus(summary.status());
        run.setEnvironment(environment);
        run.setDataSource("artifact_import");
        run.setSourceRootKey(candidate.sourceRoot());
        run.setSourceRelativePath(candidate.relativePath());
        run.setSourceSha256(sha256);
        run.setArtifactName(summary.artifactName());
        run.setArtifactUpdatedAt(toLocalDateTime(summary.updatedAt()));
        run.setImportedAt(LocalDateTime.now(ZoneOffset.UTC));
        run.setImportRevision(revision);
        run.setGateCount(summary.gateCount());
        run.setFailedGateCount(summary.failedGateCount());
        run.setReviewGateCount(summary.reviewGateCount());
        run.setEvalCaseCount(detail.evalCases().size());
        run.setTraceReferenceCount(detail.traceReferences().size());
        run.setPromptTokens(summary.tokenUsage().promptTokens());
        run.setCompletionTokens(summary.tokenUsage().completionTokens());
        run.setTotalTokens(summary.tokenUsage().totalTokens());
        run.setEstimatedCost(summary.tokenUsage().estimatedCost());
        run.setFailureBucketsJson(toJson(summary.failureBuckets()));
        run.setReviewBucketsJson(toJson(summary.reviewBuckets()));
        run.setDiagnosticsJson(toJson(detail.diagnostics()));
        run.setTraceReferencesJson(toJson(detail.traceReferences()));
        run.setArtifactMissing(summary.artifactMissing());
        run.setArtifactParseFailed(summary.artifactParseFailed());
        run.setRedactionStatus("PASS");
        return run;
    }

    private void replaceChildren(Long runId, QualityRunDetail detail) {
        qualityRunGateMapper.deleteByRunId(runId);
        qualityRunCaseMapper.deleteByRunId(runId);
        int gateIndex = 0;
        for (QualityGateSummary gate : detail.gates()) {
            QualityRunGate entity = new QualityRunGate();
            entity.setRunId(runId);
            entity.setGateName(gate.name());
            entity.setStatus(gate.status());
            entity.setPassed(gate.passed());
            entity.setMetricsJson(toJson(gate.metrics()));
            entity.setFlagsJson(toJson(gate.flags()));
            entity.setFailureBucketsJson(toJson(gate.failureBuckets()));
            entity.setReviewBucketsJson(toJson(gate.reviewBuckets()));
            entity.setSortOrder(gateIndex++);
            qualityRunGateMapper.insert(entity);
        }
        int caseIndex = 0;
        for (QualityEvalCaseResultDetail item : detail.evalCases()) {
            QualityRunCase entity = new QualityRunCase();
            entity.setRunId(runId);
            entity.setCaseId(item.caseId());
            entity.setCaseType(item.caseType());
            entity.setStatus(item.status());
            entity.setPassed(item.passed());
            entity.setTraceId(item.traceId());
            entity.setAgentRunId(item.agentRunId());
            entity.setMetricsJson(toJson(item.metrics()));
            entity.setFlagsJson(toJson(item.flags()));
            entity.setFailureBucketsJson(toJson(item.failureBuckets()));
            entity.setReviewBucketsJson(toJson(item.reviewBuckets()));
            entity.setSortOrder(caseIndex++);
            qualityRunCaseMapper.insert(entity);
        }
    }

    private void recordEvent(ImportCandidate candidate,
                             String sha256,
                             String marker,
                             String status,
                             String safeMessage,
                             Long requestedByUserId) {
        QualityImportEvent event = new QualityImportEvent();
        event.setSourceType("artifact");
        event.setSourceRootKey(candidate == null ? "" : candidate.sourceRoot());
        event.setSourceRelativePath(candidate == null ? "" : candidate.relativePath());
        event.setArtifactSha256(sha256);
        event.setMarker(marker);
        event.setStatus(status);
        event.setSafeMessage(safeMessage == null ? "" : safeMessage);
        event.setRequestedByUserId(requestedByUserId);
        event.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        event.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        qualityImportEventMapper.insert(event);
    }

    private String sha256(String raw) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new RejectedArtifactException("quality artifact snapshot serialization failed");
        }
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (cwd.getFileName() != null && "backend".equals(cwd.getFileName().toString())) {
            return cwd.getParent();
        }
        return cwd;
    }

    private boolean isInternalImportTestMarker(String marker) {
        return marker != null && marker.trim().startsWith(INTERNAL_IMPORT_TEST_MARKER_PREFIX);
    }

    private record ImportCandidate(String sourceRoot, String relativePath, Path path) {
    }

    private static final class RejectedArtifactException extends RuntimeException {
        private RejectedArtifactException(String message) {
            super(message);
        }
    }

    private static final class ImportCounter {
        private int scanned;
        private int imported;
        private int updated;
        private int skippedDuplicate;
        private int rejected;
        private int failed;

        private QualityImportResult toResult() {
            return new QualityImportResult(scanned, imported, updated, skippedDuplicate, rejected, failed);
        }
    }

    private enum ImportOutcome {
        IMPORTED,
        UPDATED,
        REJECTED
    }
}
