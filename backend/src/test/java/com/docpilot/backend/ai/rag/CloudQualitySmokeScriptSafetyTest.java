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
                .contains("KB_LIFECYCLE_DELETE")
                .contains("T22_join_immediate_query")
                .contains("T23_remove_no_evidence")
                .contains("T24_rejoin_restored")
                .contains("T25_multi_kb_isolation")
                .contains("T26_disposable_document_delete")
                .contains("knowledge base lifecycle gate requires fixed corpus gate")
                .contains("DELETE_DISPOSABLE")
                .contains("MARKER_KB_DELETE_T26")
                .contains("qdrantResidualStrategy")
                .contains("observed_only_relation_cleanup_is_hard_gate")
                .contains("Test-SafeArtifactShape")
                .contains("if ($null -eq $item.documentId)")
                .contains("Test-SafeArtifactShape $resources $checks")
                .contains("T25_a_returned_b_only_marker")
                .contains("T25_a_b_only_query_scope_violation")
                .contains("T26_deleted_marker_citation_leakage")
                .contains("postDeleteDocumentDetailOk")
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

    @Test
    void shouldOnlyCollectQualityMetricsWhenBackendWasStartedByTheSmoke() throws Exception {
        String script = Files.readString(scriptPath(), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("$script:QualityMetricsIsolated = $false")
                .contains("if (-not $script:QualityMetricsIsolated)")
                .contains("$snapshot.sampleGaps += \"metricsNotIsolated\"")
                .contains("$script:QualityMetricsIsolated = $true")
                .contains("docpilot_ai_token_usage_tokens_sum")
                .contains("docpilot_ai_call_duration_seconds_sum")
                .contains("docpilot_ai_call_duration_seconds_count");
        assertThat(script.indexOf("$script:QualityMetricsIsolated = $true"))
                .isGreaterThan(script.indexOf("Wait-BackendHealth 120"));
    }

    private static Path scriptPath() {
        return Path.of("..", "scripts", "smoke", "cloud-quality-smoke.ps1");
    }
}
