package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RagQaTraceSmokeScriptSafetyTest {

    @Test
    void shouldKeepTraceSmokeScriptSanitized() throws Exception {
        String script = Files.readString(Path.of("scripts/rag/run-rag-qa-trace-smoke.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("ragEnabled");
        assertThat(script).contains("embeddingProvider");
        assertThat(script).contains("vectorStoreType");
        assertThat(script).contains("retrievedCount");
        assertThat(script).contains("contextHashPresent");
        assertThat(script).contains("fallbackUsed");
        assertThat(script).contains("citationCount");
        assertThat(script).doesNotContain("Authorization");
        assertThat(script).doesNotContain("Bearer");
        assertThat(script).doesNotContain("AuthToken");
        assertThat(script).doesNotContain("apiKey");
        assertThat(script).doesNotContain("baseUrl");
        assertThat(script).doesNotContain("endpoint");
        assertThat(script).doesNotContain("provider response");
        assertThat(script).doesNotContain("document body");
        assertThat(script).doesNotContain("documentText");
        assertThat(script).doesNotContain("prompt");
    }
}
