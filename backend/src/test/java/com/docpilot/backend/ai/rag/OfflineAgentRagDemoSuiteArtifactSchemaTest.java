package com.docpilot.backend.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineAgentRagDemoSuiteArtifactSchemaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepOfflineDemoArtifactSchemaStableAndRedacted() throws Exception {
        Path outputPath = Files.createTempFile("offline-agent-rag-demo-suite-schema-", ".json");
        Files.deleteIfExists(outputPath);

        Process process = new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                Path.of("scripts", "agent", "run-offline-agent-rag-demo-suite.ps1").toString(),
                "-SkipTests",
                "-OutputPath",
                outputPath.toString())
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        String output = readAll(process.getInputStream());

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(Files.exists(outputPath)).isTrue();

        String json = Files.readString(outputPath, StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        assertThat(root.hasNonNull("generatedAt")).isTrue();
        assertThat(root.path("mode").asText()).isEqualTo("offline");
        assertThat(root.path("embeddingProvider").asText()).isEqualTo(RagEmbeddingProperties.PROVIDER_FAKE);
        assertThat(root.path("vectorStore").asText()).isEqualTo(RagIndexManager.VECTOR_STORE_IN_MEMORY);
        assertThat(root.path("providerHttp").asBoolean()).isFalse();
        assertThat(root.path("qdrantEnabled").asBoolean()).isFalse();
        assertThat(root.path("status").asText()).isIn("planned", "passed");
        assertThat(root.path("checks").isArray()).isTrue();
        assertThat(root.path("checks")).isNotEmpty();
        assertThat(root.path("artifactPaths").isArray()).isTrue();
        assertThat(root.path("testsOrChecksExecuted").isArray()).isTrue();

        assertForbiddenFieldsAbsent(root);
        assertThat(output + json)
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("apiKey")
                .doesNotContain("token")
                .doesNotContain("baseUrl")
                .doesNotContain("endpoint")
                .doesNotContain("prompt")
                .doesNotContain("documentText")
                .doesNotContain("providerResponse")
                .doesNotContain("http://")
                .doesNotContain("https://");
    }

    private void assertForbiddenFieldsAbsent(JsonNode node) {
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                assertThat(fieldName)
                        .isNotEqualToIgnoringCase("apiKey")
                        .isNotEqualToIgnoringCase("token")
                        .isNotEqualToIgnoringCase("Authorization")
                        .isNotEqualToIgnoringCase("baseUrl")
                        .isNotEqualToIgnoringCase("endpoint")
                        .isNotEqualToIgnoringCase("prompt")
                        .isNotEqualToIgnoringCase("documentText")
                        .isNotEqualToIgnoringCase("providerResponse");
                assertForbiddenFieldsAbsent(node.get(fieldName));
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(this::assertForbiddenFieldsAbsent);
        }
    }

    private static String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
