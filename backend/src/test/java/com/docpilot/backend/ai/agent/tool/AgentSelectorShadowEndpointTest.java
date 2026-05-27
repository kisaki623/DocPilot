package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSelectorShadowEndpointTest {

    @Test
    void shouldReturnEmptyMetricsSnapshot() {
        SelectorMetricsCollector metricsCollector = new SelectorMetricsCollector();
        AgentSelectorShadowEndpoint endpoint = new AgentSelectorShadowEndpoint(
                new SelectorMetricsDebugReporter(metricsCollector)
        );

        SelectorMetricsDebugSnapshot snapshot = endpoint.shadowMetrics();

        assertNotNull(snapshot);
        assertEquals(0L, snapshot.totalCount());
        assertTrue(snapshot.providerAggregation().isEmpty());
        assertTrue(snapshot.decisionAggregation().isEmpty());
    }

    @Test
    void shouldNotChangeMetricsWhenRead() {
        SelectorMetricsCollector metricsCollector = new SelectorMetricsCollector();
        metricsCollector.recordSuccess("fake", "summary_tool", "summary_tool");
        AgentSelectorShadowEndpoint endpoint = new AgentSelectorShadowEndpoint(
                new SelectorMetricsDebugReporter(metricsCollector)
        );
        SelectorMetricsSnapshot before = metricsCollector.snapshot();

        endpoint.shadowMetrics();
        endpoint.shadowMetrics();
        SelectorMetricsSnapshot after = metricsCollector.snapshot();

        assertEquals(before.totalCount(), after.totalCount());
        assertEquals(before.successCount(), after.successCount());
        assertEquals(before.failureCount(), after.failureCount());
        assertEquals(before.matchedCount(), after.matchedCount());
        assertEquals(before.mismatchCount(), after.mismatchCount());
    }

    @Test
    void shouldExposeOnlySafeSnapshotFields() {
        Set<String> componentNames = Arrays.stream(SelectorMetricsDebugSnapshot.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        assertTrue(componentNames.contains("totalcount"));
        assertTrue(componentNames.contains("provideraggregation"));
        assertTrue(componentNames.contains("decisionaggregation"));
        assertFalse(componentNames.contains("apikey"));
        assertFalse(componentNames.contains("baseurl"));
        assertFalse(componentNames.contains("authorization"));
        assertFalse(componentNames.contains("prompt"));
        assertFalse(componentNames.contains("task"));
        assertFalse(componentNames.contains("documentcontent"));
        assertFalse(componentNames.contains("modelrawresponse"));
        assertFalse(componentNames.contains("userid"));
        assertFalse(componentNames.contains("documentid"));
        assertFalse(componentNames.contains("sessionid"));
        assertFalse(componentNames.contains("taskinput"));
        assertFalse(componentNames.contains("finalanswer"));
    }

    @Test
    void shouldNotIncludeBlacklistedTermsInSnapshotString() {
        SelectorMetricsCollector metricsCollector = new SelectorMetricsCollector();
        metricsCollector.recordSuccess("openai_compatible", "summary_tool", "summary_tool");
        AgentSelectorShadowEndpoint endpoint = new AgentSelectorShadowEndpoint(
                new SelectorMetricsDebugReporter(metricsCollector)
        );

        String snapshotText = endpoint.shadowMetrics().toString().toLowerCase(Locale.ROOT);

        assertFalse(snapshotText.contains("apikey"));
        assertFalse(snapshotText.contains("baseurl"));
        assertFalse(snapshotText.contains("authorization"));
        assertFalse(snapshotText.contains("prompt"));
        assertFalse(snapshotText.contains("taskinput"));
        assertFalse(snapshotText.contains("documentcontent"));
        assertFalse(snapshotText.contains("modelrawresponse"));
        assertFalse(snapshotText.contains("userid"));
        assertFalse(snapshotText.contains("documentid"));
        assertFalse(snapshotText.contains("sessionid"));
        assertFalse(snapshotText.contains("finalanswer"));
    }
}
