package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagQaTraceFormatterTest {

    private static final String DOC_BODY_MARKER = "PRIVATE_DOC_BODY_MARKER_DO_NOT_DUMP";
    private static final String PROMPT_MARKER = "PRIVATE_PROMPT_MARKER_DO_NOT_DUMP";
    private static final String PROVIDER_RESPONSE_MARKER = "PRIVATE_PROVIDER_RESPONSE_MARKER_DO_NOT_DUMP";

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

    @Test
    void shouldExposeInterviewSafeSummaryFieldsOnly() {
        RagQaTrace trace = RagQaTrace.retrieval(
                "fake",
                "qdrant",
                true,
                3,
                2,
                600,
                480,
                true,
                true,
                2,
                true,
                false
        ).withCacheKeyRagAware(true);

        Map<String, Object> fields = formatter.toInterviewSafeMap(trace);
        String formatted = formatter.formatInterviewSummary(trace);

        assertThat(fields.keySet()).containsExactlyElementsOf(List.of(
                "ragEnabled",
                "embeddingProvider",
                "vectorStoreType",
                "topK",
                "retrievedCount",
                "contextHashPresent",
                "contextTruncated",
                "fallbackUsed",
                "fallbackReason",
                "citationCount",
                "indexReused",
                "cacheKeyRagAware"
        ));
        assertThat(fields)
                .containsEntry("ragEnabled", true)
                .containsEntry("embeddingProvider", "fake")
                .containsEntry("vectorStoreType", "qdrant")
                .containsEntry("topK", 3)
                .containsEntry("retrievedCount", 2)
                .containsEntry("contextHashPresent", true)
                .containsEntry("contextTruncated", true)
                .containsEntry("fallbackUsed", false)
                .containsEntry("fallbackReason", "")
                .containsEntry("citationCount", 2)
                .containsEntry("indexReused", true)
                .containsEntry("cacheKeyRagAware", true);
        assertThat(formatted).contains("ragEnabled=true");
        assertThat(formatted).contains("cacheKeyRagAware=true");
        assertThat(formatted)
                .doesNotContain("documentIdPresent")
                .doesNotContain("maxContextChars")
                .doesNotContain("contextChars")
                .doesNotContain("indexTruncated")
                .doesNotContain(DOC_BODY_MARKER)
                .doesNotContain(PROMPT_MARKER);
    }

    @Test
    void shouldFormatFallbackSummaryWithZeroRetrievalAndCitationCounts() {
        RagQaTrace trace = new RagQaTrace(
                true,
                "fake",
                "qdrant",
                true,
                4,
                0,
                800,
                640,
                true,
                true,
                true,
                "qdrant_http_error",
                3,
                true,
                false,
                false
        );

        Map<String, Object> fields = formatter.toInterviewSafeMap(trace);
        String formatted = formatter.formatInterviewSummary(trace);

        assertThat(fields)
                .containsEntry("ragEnabled", true)
                .containsEntry("embeddingProvider", "fake")
                .containsEntry("vectorStoreType", "qdrant")
                .containsEntry("topK", 4)
                .containsEntry("retrievedCount", 0)
                .containsEntry("contextHashPresent", true)
                .containsEntry("contextTruncated", true)
                .containsEntry("fallbackUsed", true)
                .containsEntry("fallbackReason", "qdrant_http_error")
                .containsEntry("citationCount", 3)
                .containsEntry("cacheKeyRagAware", true);
        assertThat(formatted)
                .contains("retrievedCount=0")
                .contains("contextHashPresent=true")
                .contains("contextTruncated=true")
                .contains("fallbackUsed=true")
                .contains("citationCount=3")
                .contains("cacheKeyRagAware=true")
                .doesNotContain(DOC_BODY_MARKER)
                .doesNotContain(PROMPT_MARKER)
                .doesNotContain(PROVIDER_RESPONSE_MARKER)
                .doesNotContain("Authorization");
    }

    @Test
    void shouldRedactUnsafeFallbackReasonTokens() {
        RagQaTrace trace = RagQaTrace.fallback(
                "fake",
                "qdrant",
                true,
                2,
                400,
                "Authorization header was present; provider response and prompt should stay hidden"
        );

        String formatted = formatter.formatInterviewSummary(trace);

        assertThat(trace.fallbackReason()).isEqualTo("redacted_fallback_reason");
        assertThat(formatted)
                .contains("fallbackReason=redacted_fallback_reason")
                .doesNotContain("Authorization")
                .doesNotContain("provider response")
                .doesNotContain("prompt")
                .doesNotContain("Bearer")
                .doesNotContain("apiKey")
                .doesNotContain("baseUrl")
                .doesNotContain("documentText");
    }

    @Test
    void shouldFormatDisabledAndQdrantDisabledStatesWithoutContextText() {
        RagQaTrace disabled = RagQaTrace.disabled("fake");
        RagQaTrace enabled = RagQaTrace.retrieval(
                "fake",
                "qdrant_disabled",
                true,
                5,
                2,
                800,
                320,
                true,
                true,
                2,
                false,
                true
        ).withCacheKeyRagAware(true);

        String disabledSummary = formatter.formatInterviewSummary(disabled);
        String enabledSummary = formatter.formatInterviewSummary(enabled);

        assertThat(disabledSummary)
                .contains("ragEnabled=false")
                .contains("embeddingProvider=fake")
                .contains("vectorStoreType=in_memory")
                .contains("retrievedCount=0")
                .contains("fallbackUsed=false")
                .contains("cacheKeyRagAware=false");
        assertThat(enabledSummary)
                .contains("ragEnabled=true")
                .contains("embeddingProvider=fake")
                .contains("vectorStoreType=qdrant_disabled")
                .contains("topK=5")
                .contains("retrievedCount=2")
                .contains("contextHashPresent=true")
                .contains("contextTruncated=true")
                .contains("fallbackUsed=false")
                .contains("citationCount=2")
                .contains("cacheKeyRagAware=true");
        assertThat(disabledSummary + enabledSummary)
                .doesNotContain(DOC_BODY_MARKER)
                .doesNotContain(PROMPT_MARKER)
                .doesNotContain(PROVIDER_RESPONSE_MARKER)
                .doesNotContain("context=")
                .doesNotContain("Authorization")
                .doesNotContain("provider response")
                .doesNotContain("documentText");
    }
}
