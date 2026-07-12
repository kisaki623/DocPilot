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
    }
}
