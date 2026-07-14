package com.docpilot.backend.ai.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextTraceSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeModelSkippedAliasForTraceJson() throws Exception {
        ContextTrace trace = new ContextTrace(
                10L,
                102L,
                "AGENT_MEMORY",
                GroundingPolicy.STRICT_KB.name(),
                RouteDecision.STRICT_NO_EVIDENCE_FALLBACK.name(),
                false,
                false,
                0,
                0,
                false,
                0,
                List.of(),
                true,
                true,
                3L,
                0,
                true,
                Map.of(),
                8000,
                20,
                false,
                List.of(),
                true,
                "STRICT_KB_NO_EVIDENCE",
                true
        );

        JsonNode json = objectMapper.valueToTree(trace);

        assertThat(json.path("modelCallSkipped").asBoolean()).isTrue();
        assertThat(json.path("modelSkipped").asBoolean()).isTrue();
        assertThat(json.has("citations")).isFalse();
    }

    @Test
    void shouldExposeOnlySafeTechnicalDetailsWithoutRawPromptOrEvidenceText() throws Exception {
        ContextTrace trace = new ContextTrace(
                10L,
                102L,
                "AGENT_MEMORY",
                GroundingPolicy.AUTO_RAG.name(),
                RouteDecision.AUTO_RAG_EVIDENCE.name(),
                true,
                true,
                1,
                2,
                true,
                1,
                List.of("PREFERENCE"),
                true,
                false,
                3L,
                1,
                false,
                Map.of(83L, 1),
                8000,
                120,
                false,
                List.of(),
                false,
                "",
                false,
                ContextTraceTechnicalDetails.build(
                        10L,
                        102L,
                        new ContextTraceTechnicalDetails.RouteDetails(
                                GroundingPolicy.AUTO_RAG.name(),
                                RouteDecision.AUTO_RAG_EVIDENCE.name(),
                                "AUTO_RAG_EVIDENCE",
                                true,
                                false,
                                false,
                                true,
                                false
                        ),
                        Map.of("retrieval", 12L, "modelCall", 36L),
                        new ContextTraceTechnicalDetails.RetrievalDetails(
                                "hybrid",
                                "qdrant",
                                6,
                                1,
                                Map.of(83L, 1),
                                true,
                                "qwen3-rerank",
                                "",
                                true,
                                2,
                                1,
                                ContextTraceTechnicalDetails.EvidenceGateDetails.passed("EVIDENCE_SELECTED"),
                                List.of(new ContextTraceTechnicalDetails.ScoreRow(
                                        1,
                                        83L,
                                        "SLA Guide",
                                        8301L,
                                        2,
                                        "page:2#block:5",
                                        0.82,
                                        0.41,
                                        0.76,
                                        0.93,
                                        0.93,
                                        true
                                ))
                        ),
                        new ContextTraceTechnicalDetails.TokenBudgetDetails(
                                8000,
                                120,
                                false,
                                List.of(new ContextTraceTechnicalDetails.TokenBudgetTypeSummary(
                                        "RAG_EVIDENCE",
                                        1,
                                        80,
                                        0,
                                        0
                                )),
                                List.of()
                        ),
                        new ContextTraceTechnicalDetails.ContextUsageDetails(
                                new ContextTraceTechnicalDetails.SummaryUsage(true),
                                new ContextTraceTechnicalDetails.MemoryUsage(true, 1, List.of("PREFERENCE")),
                                new ContextTraceTechnicalDetails.RecentUsage(1, 2)
                        ),
                        new ContextTraceTechnicalDetails.FallbackDetails(false, "", "")
                ),
                List.of()
        );

        String json = objectMapper.writeValueAsString(trace);
        JsonNode tree = objectMapper.readTree(json);

        assertThat(tree.path("technicalDetails").path("available").asBoolean()).isTrue();
        assertThat(tree.path("technicalDetails").path("traceId").asText()).isEqualTo("ctx-10-102");
        assertThat(tree.path("technicalDetails").path("retrieval").path("scoreRows").size()).isEqualTo(1);
        assertThat(json).contains("vectorScore");
        assertThat(json).doesNotContain("prompt");
        assertThat(json).doesNotContain("assembledContext");
        assertThat(json).doesNotContain("quoteText");
        assertThat(json).doesNotContain("snippet");
        assertThat(json).doesNotContain("P1 incidents respond within 10 minutes");
        assertThat(json).doesNotContain("apiKey");
        assertThat(json).doesNotContain("Authorization");
    }
}
