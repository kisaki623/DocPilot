package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantPreflightScriptSafetyTest {

    @Test
    void shouldKeepQdrantPreflightOutputSanitized() throws IOException {
        String script = Files.readString(Path.of("scripts/rag/preflight-qdrant-vector-store.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("APP_RAG_VECTOR_STORE_PROVIDER");
        assertThat(script).contains("APP_RAG_VECTOR_STORE_QDRANT_ENDPOINT");
        assertThat(script).contains("APP_RAG_VECTOR_STORE_QDRANT_COLLECTION");
        assertThat(script).contains("APP_RAG_VECTOR_STORE_QDRANT_API_KEY");
        assertThat(script).contains("appProviderPresent");
        assertThat(script).contains("appEndpointPresent");
        assertThat(script).contains("appCollectionPresent");
        assertThat(script).contains("appApiKeyPresent");
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
        assertThat(script).contains("AllowRequest");
        assertThat(script).contains("requestAllowed");
        assertThat(script).contains("requestAttempted");
        assertThat(script).contains("READY_DRY_RUN");
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

    @Test
    void shouldDefaultToDryRunAndRedactEnvironmentValues() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                Path.of("scripts", "rag", "preflight-qdrant-vector-store.ps1").toString());
        Map<String, String> env = builder.environment();
        env.put("APP_RAG_VECTOR_STORE_PROVIDER", "qdrant");
        env.put("APP_RAG_VECTOR_STORE_QDRANT_ENDPOINT", "https://qdrant.example.invalid:6333");
        env.put("APP_RAG_VECTOR_STORE_QDRANT_COLLECTION", "docpilot_eval_collection");
        env.put("APP_RAG_VECTOR_STORE_QDRANT_API_KEY", "secret-qdrant-api-key");
        Process process = builder.redirectErrorStream(true).start();

        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        String output = readAll(process.getInputStream());

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output)
                .contains("READY_DRY_RUN")
                .contains("\"providerIsQdrant\":  true")
                .contains("\"endpointPresent\":  true")
                .contains("\"collectionPresent\":  true")
                .contains("\"apiKeyPresent\":  true")
                .contains("\"requestAllowed\":  false")
                .contains("\"requestAttempted\":  false")
                .contains("\"dryRun\":  true")
                .doesNotContain("https://")
                .doesNotContain("qdrant.example.invalid")
                .doesNotContain("docpilot_eval_collection")
                .doesNotContain("secret-qdrant-api-key")
                .doesNotContain("Authorization")
                .doesNotContain("api-key")
                .doesNotContain("provider response")
                .doesNotContain("documentText")
                .doesNotContain("prompt");
    }

    private static String readAll(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
