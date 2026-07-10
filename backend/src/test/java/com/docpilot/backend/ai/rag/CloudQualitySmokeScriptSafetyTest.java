package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CloudQualitySmokeScriptSafetyTest {

    @Test
    void preservesBusinessApiFailuresWithoutMisclassifyingThemAsTransportFailures() throws Exception {
        String script = Files.readString(scriptPath(), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("function New-ApiBusinessFailure")
                .contains("$exception.Data[\"httpStatus\"] = 200")
                .contains("$exception.Data.Contains(\"httpStatus\")")
                .contains("throw (New-ApiBusinessFailure $response)")
                .doesNotContain("throw \"api returned non-zero code");
    }

    private static Path scriptPath() {
        return Path.of("..", "scripts", "smoke", "cloud-quality-smoke.ps1");
    }
}
