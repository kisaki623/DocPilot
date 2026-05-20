package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RagQaDemoScriptSafetyTest {

    @Test
    void shouldKeepDemoScriptOutputSanitized() throws IOException {
        String script = Files.readString(Path.of("scripts/rag/demo-rag-qa-fake.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("ragEnabled");
        assertThat(script).contains("embeddingProvider");
        assertThat(script).contains("vectorStoreType");
        assertThat(script).contains("contextHashPresent");
        assertThat(script).contains("cacheKeyRagAware");
        assertThat(script).contains("isLocalhost");
        assertThat(script).doesNotContain("backendBaseUrl");
        assertThat(script).doesNotContain("apiKey");
        assertThat(script).doesNotContain("API Key");
        assertThat(script).doesNotContain("provider response");
        assertThat(script).doesNotContain("document body");
        assertThat(script).doesNotContain("$data.answer");
        assertThat(script).doesNotContain("$data.citations[");
        assertThat(script).doesNotContain("Write-Host $AuthToken");
    }
}
