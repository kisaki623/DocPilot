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

    @Test
    void shouldBindStartedNextDevServerToTheRequestedLoopbackOrigin() throws Exception {
        String script = Files.readString(scriptPath(), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("$frontendUri = [Uri]$FrontendBaseUrl")
                .contains("frontend dev host must be a loopback address")
                .contains("frontend base URL must use http or https with an explicit valid port")
                .contains("npm.cmd run dev -- -H $frontendHost -p $port")
                .doesNotContain("$host = $frontendUri.Host");
        assertThat(script.indexOf("frontend dev host must be a loopback address"))
                .isLessThan(script.indexOf("if (Wait-FrontendRoute 3)"));
    }

    @Test
    void shouldKeepKnowledgeBaseLifecycleGateBoundedAndDependentOnFixedCorpus() throws Exception {
        String script = Files.readString(scriptPath(), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("EnableKnowledgeBaseLifecycleGate")
                .contains("Invoke-KnowledgeBaseLifecycleGate")
                .contains("knowledgeBaseLifecyclePlanContract")
                .contains("knowledgeBaseLifecycleGateEnabled")
                .contains("knowledgeBaseLifecycleGate")
                .contains("KB_LIFECYCLE_A")
                .contains("KB_LIFECYCLE_B")
                .contains("T22_join_immediate_query")
                .contains("T23_remove_no_evidence")
                .contains("T24_rejoin_restored")
                .contains("T25_multi_kb_isolation")
                .contains("knowledge base lifecycle gate requires fixed corpus gate")
                .contains("shared_fixture_must_remain_inspectable")
                .contains("Test-SafeArtifactShape")
                .contains("if ($null -eq $item.documentId)")
                .contains("Test-SafeArtifactShape $resources $checks")
                .contains("T25_a_returned_b_only_marker")
                .contains("T25_a_b_only_query_scope_violation")
                .contains("retrieveBContractAfterSharedRemove")
                .contains("dependencySatisfied")
                .contains("blockedReason")
                .doesNotContain("rawAnswer =")
                .doesNotContain("rawResponse =")
                .doesNotContain("prompt =")
                .doesNotContain("evidenceContext =")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("apiKey =");
    }

    private static Path scriptPath() {
        return Path.of("..", "scripts", "smoke", "cloud-quality-smoke.ps1");
    }
}
