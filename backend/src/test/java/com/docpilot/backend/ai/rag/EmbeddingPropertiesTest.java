package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingPropertiesTest {

    @Test
    void shouldUseMockProviderByDefault() {
        EmbeddingProperties properties = new EmbeddingProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getProvider()).isEqualTo(EmbeddingProperties.PROVIDER_MOCK);
        assertThat(properties.isMockProvider()).isTrue();
        assertThat(properties.getDimension()).isEqualTo(32);
        assertThat(properties.getConnectTimeoutMs()).isEqualTo(5000);
        assertThat(properties.getRequestTimeoutMs()).isEqualTo(30000);
    }

    @Test
    void shouldNormalizeProviderAliases() {
        EmbeddingProperties properties = new EmbeddingProperties();

        properties.setProvider("fake");
        assertThat(properties.getProvider()).isEqualTo(EmbeddingProperties.PROVIDER_MOCK);

        properties.setProvider("openai-compatible");
        assertThat(properties.getProvider()).isEqualTo(EmbeddingProperties.PROVIDER_OPENAI_COMPATIBLE);

        properties.setProvider("openaiCompatible");
        assertThat(properties.getProvider()).isEqualTo(EmbeddingProperties.PROVIDER_OPENAI_COMPATIBLE);
    }

    @Test
    void shouldTreatDisabledFlagAsDisabledProvider() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(false);

        assertThat(properties.isDisabledProvider()).isTrue();
    }

    @Test
    void shouldRejectInvalidValues() {
        EmbeddingProperties properties = new EmbeddingProperties();

        assertThatThrownBy(() -> properties.setProvider("qdrant"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported embedding provider");
        assertThatThrownBy(() -> properties.setDimension(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimension");
        assertThatThrownBy(() -> properties.setConnectTimeoutMs(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectTimeoutMs");
        assertThatThrownBy(() -> properties.setRequestTimeoutMs(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestTimeoutMs");
    }
}
