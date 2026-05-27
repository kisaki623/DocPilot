package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingProviderPreflightScriptSafetyTest {

    @Test
    void shouldKeepEmbeddingProviderPreflightDryRunAndSanitized() throws IOException {
        String script = Files.readString(Path.of("scripts/rag/preflight-embedding-provider.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("APP_RAG_EMBEDDING_PROVIDER");
        assertThat(script).contains("APP_RAG_EMBEDDING_BASE_URL");
        assertThat(script).contains("APP_RAG_EMBEDDING_MODEL");
        assertThat(script).contains("APP_RAG_EMBEDDING_API_KEY");
        assertThat(script).contains("providerPresent");
        assertThat(script).contains("baseUrlPresent");
        assertThat(script).contains("modelPresent");
        assertThat(script).contains("apiKeyPresent");
        assertThat(script).contains("requiredConfigPresent");
        assertThat(script).contains("READY_DRY_RUN");
        assertThat(script).contains("realEmbeddingRuntimeBlocked");
        assertThat(script).contains("httpAttempted = $false");
        assertThat(script).contains("dryRun = $true");
        assertThat(script).doesNotContain("backend/.env");
        assertThat(script).doesNotContain("Get-Content");
        assertThat(script).doesNotContain("Invoke-WebRequest");
        assertThat(script).doesNotContain("Invoke-RestMethod");
        assertThat(script).doesNotContain("HttpClient");
        assertThat(script).doesNotContain("Authorization");
        assertThat(script).doesNotContain("Write-Host $baseUrl");
        assertThat(script).doesNotContain("Write-Output $baseUrl");
        assertThat(script).doesNotContain("Write-Host $apiKey");
        assertThat(script).doesNotContain("Write-Output $apiKey");
        assertThat(script).doesNotContain("ResponseBody");
        assertThat(script).doesNotContain("provider response");
        assertThat(script).doesNotContain("document body");
        assertThat(script).doesNotContain("prompt");
    }
}
