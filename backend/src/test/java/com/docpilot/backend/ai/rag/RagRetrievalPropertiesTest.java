package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RagRetrievalPropertiesTest {

    @Test
    void shouldKeepProgrammaticDefaultDisabledForOfflineHarnesses() {
        RagRetrievalProperties properties = new RagRetrievalProperties();

        assertThat(properties.getMinSimilarityThreshold()).isEqualTo(0.0D);
        assertThat(properties.isMultiQueryEnabled()).isFalse();
        assertThat(properties.getMaxQueryVariants()).isEqualTo(3);
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

    @Test
    void shouldRejectInvalidMaxQueryVariants() {
        RagRetrievalProperties properties = new RagRetrievalProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setMaxQueryVariants(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxQueryVariants(6));

        properties.setMaxQueryVariants(5);

        assertThat(properties.getMaxQueryVariants()).isEqualTo(5);
    }
}
