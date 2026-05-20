package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagQaContextBuilderTest {

    @Test
    void shouldBuildLimitedRagQaContext() {
        RagQaContextBuilder builder = new RagQaContextBuilder();

        RagQaContext context = builder.build(
                61L,
                "Where is Redis cache used?",
                """
                        Redis cache keeps hot session context and token bucket counters.
                        RocketMQ outbox dispatches parser tasks to consumers.
                        MinIO stores uploaded objects.
                        """,
                2,
                120
        );

        assertThat(context.used()).isTrue();
        assertThat(context.chunkCount()).isGreaterThanOrEqualTo(1);
        assertThat(context.retrievedCount()).isLessThanOrEqualTo(2);
        assertThat(context.contextText()).hasSizeLessThanOrEqualTo(120);
        assertThat(context.contextText()).contains("documentId=61");
        assertThat(context.citations()).isNotEmpty();
    }

    @Test
    void shouldReturnEmptyWhenDocumentTextMissing() {
        RagQaContextBuilder builder = new RagQaContextBuilder();

        RagQaContext context = builder.build(61L, "question", "   ", 3, 200);

        assertThat(context.used()).isFalse();
        assertThat(context.contextText()).isEmpty();
        assertThat(context.citations()).isEmpty();
        assertThat(context.chunkCount()).isZero();
        assertThat(context.retrievedCount()).isZero();
    }

    @Test
    void shouldRespectMaxContextChars() {
        RagQaContextBuilder builder = new RagQaContextBuilder();

        RagQaContext context = builder.build(
                61L,
                "cache",
                "Redis cache ".repeat(200),
                3,
                50
        );

        assertThat(context.used()).isTrue();
        assertThat(context.contextText()).hasSizeLessThanOrEqualTo(50);
    }
}
