package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagDebugReporterTest {

    private static final String DOC_BODY_MARKER = "PRIVATE_DOC_BODY_MARKER_DO_NOT_DUMP";
    private static final String PROMPT_MARKER = "PRIVATE_PROMPT_MARKER_DO_NOT_DUMP";
    private static final String SECRET_MARKER = "SECRET_MARKER_DO_NOT_DUMP";
    private static final String ENDPOINT_MARKER = "http://private-provider.example.invalid";

    private final RagDebugReporter reporter = new RagDebugReporter();

    @Test
    void shouldBuildSnapshotFromNormalRagContext() {
        RagQaContext context = new RagQaContextBuilder().build(
                61L,
                "Where is Redis cache used? " + PROMPT_MARKER,
                "Redis cache keeps hot session context. " + DOC_BODY_MARKER,
                2,
                200
        );

        RagDebugSnapshot snapshot = RagDebugSnapshot.fromContext(context);
        Map<String, Object> fields = reporter.toSafeMap(snapshot);

        assertThat(snapshot.ragEnabled()).isTrue();
        assertThat(snapshot.embeddingProvider()).isEqualTo("fake");
        assertThat(snapshot.vectorStoreType()).isEqualTo("in_memory");
        assertThat(snapshot.documentIdPresent()).isTrue();
        assertThat(snapshot.retrievedCount()).isGreaterThan(0);
        assertThat(snapshot.chunkCount()).isGreaterThan(0);
        assertThat(snapshot.contextHashPresent()).isTrue();
        assertThat(fields).containsKeys("ragEnabled", "chunkCount", "indexReused", "indexTruncated");
    }

    @Test
    void shouldBuildSnapshotFromFallbackTrace() {
        RagQaTrace trace = RagQaTrace.fallback(
                "fake",
                "qdrant",
                true,
                3,
                500,
                "qdrant_timeout " + SECRET_MARKER
        );

        RagDebugSnapshot snapshot = RagDebugSnapshot.fromTrace(trace, 0, true);
        String formatted = reporter.format(snapshot);

        assertThat(snapshot.fallbackUsed()).isTrue();
        assertThat(snapshot.fallbackReason()).contains("qdrant_timeout");
        assertThat(snapshot.userIdPresent()).isTrue();
        assertThat(formatted).contains("fallbackUsed=true");
        assertThat(formatted).doesNotContain(SECRET_MARKER);
    }

    @Test
    void shouldHandleNullSnapshotAndTrace() {
        assertThat(RagDebugSnapshot.fromTrace(null)).isEqualTo(RagDebugSnapshot.empty());
        assertThat(reporter.toSafeMap(null)).containsEntry("ragEnabled", false);
        assertThat(reporter.format(null)).contains("ragEnabled=false");
    }

    @Test
    void shouldNotExposeSensitiveFieldsInStringOutput() {
        RagDebugSnapshot snapshot = new RagDebugSnapshot(
                true,
                "fake",
                "in_memory",
                "in_memory",
                true,
                true,
                3,
                1,
                2,
                false,
                false,
                120,
                false,
                true,
                false,
                "safe_fallback",
                1,
                true
        );

        String formatted = reporter.format(snapshot)
                + reporter.toSafeMap(snapshot);

        assertThat(formatted)
                .doesNotContain(DOC_BODY_MARKER)
                .doesNotContain(PROMPT_MARKER)
                .doesNotContain(SECRET_MARKER)
                .doesNotContain(ENDPOINT_MARKER)
                .doesNotContain("Authorization")
                .doesNotContain("apiKey")
                .doesNotContain("provider response");
    }
}
