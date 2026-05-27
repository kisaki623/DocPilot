package com.docpilot.backend.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagQaTraceSmokeEvidenceTest {

    private static final Path REPORT_PATH = Path.of("target", "rag-evidence", "rag-qa-trace-summary.json");
    private static final String DOCUMENT_BODY_MARKER = "PRIVATE_DOC_BODY_MARKER_DO_NOT_DUMP";
    private static final String PROMPT_MARKER = "PRIVATE_PROMPT_MARKER_DO_NOT_DUMP";
    private static final String SECRET_MARKER = "SECRET_MARKER_DO_NOT_DUMP";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldWriteSanitizedRagQaTraceEvidenceReport() throws Exception {
        RagQaContext context = new RagQaContextBuilder().build(
                61L,
                "Where does DocPilot keep cache state? " + PROMPT_MARKER,
                "Redis cache keeps hot session context and rate limit counters. "
                        + "Agent traces keep tool metadata. "
                        + DOCUMENT_BODY_MARKER + " " + SECRET_MARKER,
                3,
                400
        );
        RagDebugReporter reporter = new RagDebugReporter();
        RagDebugSnapshot snapshot = RagDebugSnapshot.fromTrace(context.trace(), context.chunkCount(), true);
        Map<String, Object> safeReport = reporter.toSafeMap(snapshot);

        Files.createDirectories(REPORT_PATH.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), safeReport);

        String report = Files.readString(REPORT_PATH, StandardCharsets.UTF_8);
        assertThat(context.used()).isTrue();
        assertThat(safeReport).containsEntry("ragEnabled", true);
        assertThat(safeReport).containsEntry("embeddingProvider", "fake");
        assertThat(safeReport).containsEntry("vectorStoreType", "in_memory");
        assertThat(safeReport).containsEntry("documentIdPresent", true);
        assertThat(safeReport).containsEntry("userIdPresent", true);
        assertThat((Integer) safeReport.get("retrievedCount")).isGreaterThan(0);
        assertThat((Integer) safeReport.get("citationCount")).isGreaterThan(0);
        assertThat(safeReport).containsEntry("contextHashPresent", true);
        assertThat(report).contains("\"ragEnabled\" : true");
        assertThat(report).contains("\"embeddingProvider\" : \"fake\"");
        assertThat(report).contains("\"vectorStoreType\" : \"in_memory\"");
        assertThat(report)
                .doesNotContain(DOCUMENT_BODY_MARKER)
                .doesNotContain(PROMPT_MARKER)
                .doesNotContain(SECRET_MARKER)
                .doesNotContain("Authorization")
                .doesNotContain("apiKey")
                .doesNotContain("provider response")
                .doesNotContain("document body")
                .doesNotContain("prompt");
    }
}
