package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantPreflightScriptSafetyTest {

    @Test
    void shouldKeepQdrantPreflightOutputSanitized() throws IOException {
        String script = Files.readString(Path.of("scripts/rag/preflight-qdrant-vector-store.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("RAG_VECTOR_STORE_PROVIDER");
        assertThat(script).contains("RAG_QDRANT_ENDPOINT");
        assertThat(script).contains("RAG_QDRANT_COLLECTION");
        assertThat(script).contains("RAG_QDRANT_API_KEY");
        assertThat(script).contains("RAG_QDRANT_CONNECT_TIMEOUT_MS");
        assertThat(script).contains("RAG_QDRANT_REQUEST_TIMEOUT_MS");
        assertThat(script).contains("endpointPresent");
        assertThat(script).contains("collectionPresent");
        assertThat(script).contains("apiKeyPresent");
        assertThat(script).contains("connectTimeoutPresent");
        assertThat(script).contains("requestTimeoutPresent");
        assertThat(script).contains("isLocalhost");
        assertThat(script).contains("requestAttempted");
        assertThat(script).contains("AllowCreateCollection");
        assertThat(script).contains("DryRun");
        assertThat(script).contains("createAttempted");
        assertThat(script).contains("VectorSize");
        assertThat(script).contains("Distance");
        assertThat(script).contains("-Method Get");
        assertThat(script).contains("-Method Put");
        assertThat(script).doesNotContain("Authorization");
        assertThat(script).doesNotContain("Write-Host $endpoint");
        assertThat(script).doesNotContain("Write-Output $endpoint");
        assertThat(script).doesNotContain("Write-Host $apiKey");
        assertThat(script).doesNotContain("Write-Output $apiKey");
        assertThat(script).doesNotContain("Write-Host $response");
        assertThat(script).doesNotContain("Write-Output $response");
        assertThat(script).doesNotContain("ResponseBody");
        assertThat(script).doesNotContain("provider response");
        assertThat(script).doesNotContain("document body");
        assertThat(script).doesNotContain("prompt");
    }
}
