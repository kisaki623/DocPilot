package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisabledEmbeddingModelTest {

    @Test
    void shouldFailWithoutNetworkWhenEmbeddingProviderIsDisabled() {
        DisabledEmbeddingModel model = new DisabledEmbeddingModel();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> model.embed("safe input"));

        assertTrue(ex.getMessage().contains("disabled"));
    }
}
