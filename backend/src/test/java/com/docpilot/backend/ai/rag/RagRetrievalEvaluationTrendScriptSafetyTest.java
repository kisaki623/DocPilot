package com.docpilot.backend.ai.rag;

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
                "powershell",
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

    private static String readAll(InputStream inputStream) throws Exception {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
