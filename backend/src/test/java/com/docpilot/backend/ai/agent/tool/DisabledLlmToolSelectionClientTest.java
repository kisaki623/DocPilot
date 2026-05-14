package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisabledLlmToolSelectionClientTest {

    private final DisabledLlmToolSelectionClient client = new DisabledLlmToolSelectionClient();

    @Test
    void shouldReturnDisabledResponse() {
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt("select a tool");

        assertTrue(response.disabled());
        assertEquals("disabled", response.provider());
        assertEquals("disabled", response.model());
        assertFalse(response.errorMessage().isBlank());
        assertEquals("", response.rawText());
    }

    @Test
    void shouldNotFailForBlankPrompt() {
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt("   ");

        assertTrue(response.disabled());
        assertFalse(response.errorMessage().isBlank());
    }

    @Test
    void shouldNotFailForNullPrompt() {
        LlmToolSelectionClientResponse response = client.completeSelectionPrompt(null);

        assertTrue(response.disabled());
        assertFalse(response.errorMessage().isBlank());
    }
}
