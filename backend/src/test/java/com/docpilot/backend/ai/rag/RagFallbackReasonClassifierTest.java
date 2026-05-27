package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class RagFallbackReasonClassifierTest {

    @Test
    void shouldClassifyQdrantHttpErrorWithoutResponseBody() {
        IllegalStateException exception = new IllegalStateException("Qdrant search request failed with status 500.");

        assertThat(RagFallbackReasonClassifier.classify(exception)).isEqualTo("qdrant_http_error");
    }

    @Test
    void shouldClassifyNestedTimeout() {
        IllegalStateException exception = new IllegalStateException("Qdrant search request failed.",
                new HttpTimeoutException("request timed out"));

        assertThat(RagFallbackReasonClassifier.classify(exception)).isEqualTo("qdrant_timeout");
    }

    @Test
    void shouldClassifyDisabledQdrant() {
        IllegalStateException exception = new IllegalStateException(
                "Qdrant vector store is disabled; skeleton does not perform HTTP requests.");

        assertThat(RagFallbackReasonClassifier.classify(exception)).isEqualTo("qdrant_disabled");
    }

    @Test
    void shouldFallbackToGenericSafeReason() {
        IllegalStateException exception = new IllegalStateException("unexpected local failure");

        assertThat(RagFallbackReasonClassifier.classify(exception)).isEqualTo("rag_retrieval_failed");
    }
}
