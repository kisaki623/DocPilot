package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RagRetrievalPropertiesTest {

    @Test
    void shouldKeepProgrammaticDefaultDisabledForOfflineHarnesses() {
        RagRetrievalProperties properties = new RagRetrievalProperties();

        assertThat(properties.getMinSimilarityThreshold()).isEqualTo(0.0D);
    }

    @Test
    void shouldAcceptQualityGateThreshold() {
        RagRetrievalProperties properties = new RagRetrievalProperties();

        properties.setMinSimilarityThreshold(0.50D);

        assertThat(properties.getMinSimilarityThreshold()).isEqualTo(0.50D);
    }

    @Test
    void shouldRejectInvalidThreshold() {
        RagRetrievalProperties properties = new RagRetrievalProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setMinSimilarityThreshold(-0.01D));
        assertThrows(IllegalArgumentException.class, () -> properties.setMinSimilarityThreshold(1.01D));
    }
}
