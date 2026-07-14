package com.docpilot.backend.ai.rag;

import com.docpilot.backend.testutil.PowerShellTestSupport;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrievalEvaluationTrendScriptSafetyTest {

    @Test
    void shouldPrintSanitizedTrendSummaryFromOfflineHistory() throws Exception {
        Process process = new ProcessBuilder(
                PowerShellTestSupport.executable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                Path.of("scripts", "rag", "show-rag-eval-trend.ps1").toString())
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        String output = readAll(process.getInputStream());

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output)
                .contains("offline-rag-eval-trend-summary")
                .contains("latestHitRate")
                .contains("caseCount")
                .contains("previousHitRatePresent")
                .contains("deltaPresent")
                .contains("in_memory")
                .contains("fake_server")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("apiKey")
                .doesNotContain("baseUrl")
                .doesNotContain("endpoint")
                .doesNotContain("provider response")
                .doesNotContain("documentText")
                .doesNotContain("Synthetic cache evidence")
                .doesNotContain("127.0.0.1");
    }

    @Test
    void shouldKeepTrendScriptOfflineOnly() throws Exception {
        String script = Files.readString(Path.of("scripts", "rag", "show-rag-eval-trend.ps1"),
                StandardCharsets.UTF_8);

        assertThat(script)
                .contains("offline-retrieval-evaluation-history.json")
                .contains("previousHitRate")
                .contains("delta")
                .doesNotContain("Invoke-WebRequest")
                .doesNotContain("Invoke-RestMethod")
                .doesNotContain("Authorization")
                .doesNotContain("DOCPILOT_AUTH_TOKEN")
                .doesNotContain("backend/.env");
    }

    @Test
    void shouldComputeStableTrendDeltaFromOfflineHistoryArtifact() throws Exception {
        Path historyPath = Files.createTempFile("offline-rag-eval-history-", ".json");
        Files.writeString(historyPath, """
                {
                  "artifact": "offline-retrieval-evaluation-history",
                  "metricDefinition": "hitCount counts cases whose expected hit/miss behavior passed",
                  "entries": [
                    {
                      "generatedAt": "2026-05-20T00:00:00Z",
                      "vectorStoreProvider": "in_memory",
                      "embeddingProvider": "fake",
                      "caseCount": 4,
                      "hitCount": 2,
                      "missCount": 2,
                      "hitRate": "0.5000"
                    },
                    {
                      "generatedAt": "2026-05-21T00:00:00Z",
                      "vectorStoreProvider": "in_memory",
                      "embeddingProvider": "fake",
                      "caseCount": 4,
                      "hitCount": 3,
                      "missCount": 1,
                      "hitRate": "0.7500"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(
                PowerShellTestSupport.executable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                Path.of("scripts", "rag", "show-rag-eval-trend.ps1").toString(),
                "-HistoryPath",
                historyPath.toString())
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        String output = readAll(process.getInputStream());

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output)
                .contains("offline-rag-eval-trend-summary")
                .containsPattern("\"caseCount\"\\s*:\\s*4")
                .containsPattern("\"latestHitRate\"\\s*:\\s*\"0.7500\"")
                .containsPattern("\"previousHitRatePresent\"\\s*:\\s*true")
                .containsPattern("\"previousHitRate\"\\s*:\\s*\"0.5000\"")
                .containsPattern("\"deltaPresent\"\\s*:\\s*true")
                .containsPattern("\"delta\"\\s*:\\s*\"\\+0.2500\"")
                .containsPattern("\"embeddingProvider\"\\s*:\\s*\"fake\"")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("apiKey")
                .doesNotContain("baseUrl")
                .doesNotContain("endpoint")
                .doesNotContain("provider response")
                .doesNotContain("documentText")
                .doesNotContain("prompt");
    }

    private static String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
