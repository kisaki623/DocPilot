package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RagVectorStoreOfflineDemoScriptSafetyTest {

    @Test
    void shouldKeepOfflineDemoScriptSanitized() throws Exception {
        String script = Files.readString(Path.of("scripts/rag/run-rag-vector-store-offline-demo.ps1"),
                StandardCharsets.UTF_8);

        assertThat(script).contains("provider=in_memory");
        assertThat(script).contains("embeddingProvider=fake");
        assertThat(script).contains("qdrantMode=fake_server");
        assertThat(script).contains("rag-vector-store-offline-demo-summary.json");
        assertThat(script).doesNotContain("Authorization");
        assertThat(script).doesNotContain("Bearer");
        assertThat(script).doesNotContain("AuthToken");
        assertThat(script).doesNotContain("apiKey");
        assertThat(script).doesNotContain("API Key");
        assertThat(script).doesNotContain("baseUrl");
        assertThat(script).doesNotContain("provider response");
        assertThat(script).doesNotContain("documentText");
        assertThat(script).doesNotContain("prompt");
    }
}
