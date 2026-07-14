package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.entity.QualityRun;
import com.docpilot.backend.quality.entity.QualityImportEvent;
import com.docpilot.backend.quality.entity.QualityRunCase;
import com.docpilot.backend.quality.entity.QualityRunGate;
import com.docpilot.backend.quality.mapper.QualityImportEventMapper;
import com.docpilot.backend.quality.mapper.QualityRunCaseMapper;
import com.docpilot.backend.quality.mapper.QualityRunGateMapper;
import com.docpilot.backend.quality.mapper.QualityRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityArtifactImportServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QualityRunMapper runMapper = mock(QualityRunMapper.class);
    private final QualityRunGateMapper gateMapper = mock(QualityRunGateMapper.class);
    private final QualityRunCaseMapper caseMapper = mock(QualityRunCaseMapper.class);
    private final QualityImportEventMapper eventMapper = mock(QualityImportEventMapper.class);
    @TempDir
    private Path repoRoot;

    @Test
    void shouldImportCleanArtifactIntoPersistedRunSnapshot() throws Exception {
        String marker = "docpilot-agent-quality-eval-clean-" + System.nanoTime();
        writeArtifact(marker, """
                {
                  "smokeMarker": "%s",
                  "status": "PASS",
                  "tokenUsage": {"totalTokens": 17},
                  "gates": {
                    "qualityConsoleHealth": {
                      "status": "PASS",
                      "passed": true,
                      "casePassRate": 1.0
                    }
                  },
                  "caseResults": [
                    {
                      "caseId": "agent-rag-evidence-trace",
                      "caseType": "rag",
                      "status": "PASS",
                      "passed": true,
                      "metrics": {"casePassRate": 1.0}
                    }
                  ]
                }
                """.formatted(marker));
        doAnswer(invocation -> {
            QualityRun run = invocation.getArgument(0);
            run.setId(71L);
            return 1;
        }).when(runMapper).insert(any(QualityRun.class));
        QualityArtifactImportServiceImpl service = service();

        var result = service.importRecentArtifacts(1, 7L);

        assertThat(result.imported()).isEqualTo(1);
        verify(runMapper).insert(any(QualityRun.class));
        verify(gateMapper).insert(any(QualityRunGate.class));
        verify(caseMapper).insert(any(QualityRunCase.class));
        verify(eventMapper).insert(any(QualityImportEvent.class));
    }

    @Test
    void shouldSkipDuplicateArtifactDigest() throws Exception {
        String marker = "docpilot-agent-quality-eval-duplicate-" + System.nanoTime();
        writeArtifact(marker, """
                {"smokeMarker":"%s","status":"PASS","gates":{"g":{"status":"PASS"}}}
                """.formatted(marker));
        when(runMapper.selectBySourceSha256(anyString())).thenReturn(new QualityRun());
        QualityArtifactImportServiceImpl service = service();

        var result = service.importRecentArtifacts(1, 7L);

        assertThat(result.skippedDuplicate()).isEqualTo(1);
        verify(runMapper, never()).insert(any(QualityRun.class));
        verify(eventMapper).insert(any(QualityImportEvent.class));
    }

    @Test
    void shouldRejectArtifactWhenRedactionScanFindsSecretShape() throws Exception {
        String marker = "docpilot-agent-quality-eval-redaction-" + System.nanoTime();
        writeArtifact(marker, """
                {
                  "smokeMarker": "%s",
                  "status": "PASS",
                  "gates": {"g":{"status":"PASS"}},
                  "provider": "Authorization: Bearer %s"
                }
                """.formatted(marker, "abcdefghijklmnopqrstuvwxyz"));
        QualityArtifactImportServiceImpl service = service();

        var result = service.importRecentArtifacts(1, 7L);

        assertThat(result.rejected()).isEqualTo(1);
        verify(runMapper, never()).insert(any(QualityRun.class));
        verify(eventMapper).insert(any(QualityImportEvent.class));
    }

    @Test
    void shouldIgnoreReservedImportTestMarkerBeforePersistingRun() throws Exception {
        String marker = "docpilot-import-clean-" + System.nanoTime();
        writeArtifact(marker, """
                {"smokeMarker":"%s","status":"PASS","gates":{"g":{"status":"PASS"}}}
                """.formatted(marker));
        QualityArtifactImportServiceImpl service = service();

        var result = service.importRecentArtifacts(1, 7L);

        assertThat(result.scanned()).isZero();
        assertThat(result.rejected()).isZero();
        verify(runMapper, never()).insert(any(QualityRun.class));
        verify(eventMapper, never()).insert(any(QualityImportEvent.class));
    }

    @Test
    void shouldNotLetReservedImportTestMarkerConsumeImportLimit() throws Exception {
        String realMarker = "docpilot-agent-quality-eval-real-" + System.nanoTime();
        String testMarker = "docpilot-import-clean-" + System.nanoTime();
        Path realArtifact = writeArtifact(realMarker, """
                {"smokeMarker":"%s","status":"PASS","gates":{"g":{"status":"PASS"}}}
                """.formatted(realMarker));
        Path testArtifact = writeArtifact(testMarker, """
                {"smokeMarker":"%s","status":"PASS","gates":{"g":{"status":"PASS"}}}
                """.formatted(testMarker));
        Files.setLastModifiedTime(realArtifact, FileTime.from(Instant.parse("2026-07-14T01:00:00Z")));
        Files.setLastModifiedTime(testArtifact, FileTime.from(Instant.parse("2026-07-14T02:00:00Z")));
        doAnswer(invocation -> {
            QualityRun run = invocation.getArgument(0);
            run.setId(72L);
            return 1;
        }).when(runMapper).insert(any(QualityRun.class));
        QualityArtifactImportServiceImpl service = service();

        var result = service.importRecentArtifacts(1, 7L);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(1);
        verify(runMapper).insert(any(QualityRun.class));
        verify(gateMapper).insert(any(QualityRunGate.class));
    }

    private QualityArtifactImportServiceImpl service() {
        return new QualityArtifactImportServiceImpl(
                repoRoot,
                new QualityArtifactServiceImpl(repoRoot, objectMapper),
                runMapper,
                gateMapper,
                caseMapper,
                eventMapper,
                objectMapper,
                new NoopTransactionManager(),
                "local",
                2_097_152L
        );
    }

    private Path writeArtifact(String marker, String json) throws Exception {
        Path artifact = repoRoot
                .resolve("backend/target/agent-quality-eval")
                .resolve(marker)
                .resolve("artifact.json");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, json, StandardCharsets.UTF_8);
        return artifact;
    }

    private static final class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
