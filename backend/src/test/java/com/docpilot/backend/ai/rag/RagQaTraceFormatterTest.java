package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagQaTraceFormatterTest {

    private static final String DOC_BODY_MARKER = "PRIVATE_DOC_BODY_MARKER_DO_NOT_DUMP";
    private static final String PROMPT_MARKER = "PRIVATE_PROMPT_MARKER_DO_NOT_DUMP";

    private final RagQaTraceFormatter formatter = new RagQaTraceFormatter();

    @Test
    void shouldFormatTraceWithoutDocumentBodyOrPrompt() {
        RagQaContextBuilder builder = new RagQaContextBuilder();
        RagQaContext context = builder.build(
                61L,
                "Where is Redis cache used? " + PROMPT_MARKER,
                "Redis cache keeps hot session context and rate limit counters. " + DOC_BODY_MARKER,
                2,
                200
        );

        String formatted = formatter.format(context.trace());

        assertThat(context.trace().contextHashPresent()).isTrue();
        assertThat(formatted).contains("ragEnabled=true");
        assertThat(formatted).contains("embeddingProvider=fake");
        assertThat(formatted).contains("vectorStoreType=in_memory");
        assertThat(formatted).contains("documentIdPresent=true");
        assertThat(formatted).doesNotContain(DOC_BODY_MARKER);
        assertThat(formatted).doesNotContain(PROMPT_MARKER);
    }

    @Test
    void shouldExposeContextTruncationAndSafeCounts() {
        RagQaContext context = new RagQaContextBuilder().build(
                61L,
                "cache",
                "Redis cache ".repeat(200),
                3,
                40
        );

        RagQaTrace trace = context.trace();

        assertThat(trace.contextTruncated()).isTrue();
        assertThat(trace.contextChars()).isLessThanOrEqualTo(40);
        assertThat(trace.retrievedCount()).isGreaterThan(0);
        assertThat(trace.citationCount()).isGreaterThan(0);
        assertThat(formatter.toSafeMap(trace)).containsEntry("contextTruncated", true);
        assertThat(formatter.toSafeMap(trace)).containsKey("indexTruncated");
    }

    @Test
    void shouldRepresentFallbackReasonAndCacheKeyAwareness() {
        RagQaTrace fallback = RagQaTrace.fallback(
                "fake",
                true,
                3,
                2000,
                "IllegalStateException"
        ).withCacheKeyRagAware(true);

        String formatted = formatter.format(fallback);

        assertThat(fallback.fallbackUsed()).isTrue();
        assertThat(fallback.fallbackReason()).isEqualTo("IllegalStateException");
        assertThat(fallback.cacheKeyRagAware()).isTrue();
        assertThat(formatted).contains("fallbackUsed=true");
        assertThat(formatted).contains("fallbackReason=IllegalStateException");
        assertThat(formatted).contains("cacheKeyRagAware=true");
    }
}
