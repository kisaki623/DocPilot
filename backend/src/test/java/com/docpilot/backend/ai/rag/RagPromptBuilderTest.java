package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagPromptBuilderTest {

    @Test
    void shouldAlignEvidenceNumbersWithCitations() {
        RagPrompt prompt = new RagPromptBuilder().build(
                "How does cache work?",
                List.of(hit(1, 0, "Cache uses Redis."), hit(2, 1, "Rate limit uses token bucket.")),
                Map.of("title", "System Design"),
                1000
        );

        assertThat(prompt.noEvidence()).isFalse();
        assertThat(prompt.systemPrompt()).contains("Answer only from the provided evidence");
        assertThat(prompt.evidenceContext()).contains("[1]").contains("[2]");
        assertThat(prompt.evidenceContext()).contains("chunkIndex=0").contains("chunkIndex=1");
        assertThat(prompt.userPrompt()).contains("using only the numbered evidence");
    }

    @Test
    void shouldNotBuildEvidenceContextWhenNoEvidence() {
        RagPrompt prompt = new RagPromptBuilder().build("missing?", List.of(), Map.of(), 100);

        assertThat(prompt.noEvidence()).isTrue();
        assertThat(prompt.evidenceContext()).isBlank();
        assertThat(prompt.userPrompt()).contains("No evidence was retrieved");
    }

    @Test
    void shouldTruncateOnlyAtEvidenceBoundaryWhenPossible() {
        RagPrompt prompt = new RagPromptBuilder().build(
                "question",
                List.of(
                        hit(1, 0, "A".repeat(40)),
                        hit(2, 1, "B".repeat(40)),
                        hit(3, 2, "C".repeat(40))
                ),
                Map.of(),
                150
        );

        assertThat(prompt.evidenceContext()).contains("[1]");
        assertThat(prompt.evidenceContext()).doesNotContain("[3]");
        assertThat(prompt.evidenceContext()).doesNotEndWith("[");
    }

    private RagRetrievalHit hit(int citationIndex, int chunkIndex, String content) {
        return new RagRetrievalHit(
                citationIndex,
                "vector-" + citationIndex,
                0.9D - chunkIndex * 0.1D,
                7L,
                101L,
                1,
                500L + chunkIndex,
                chunkIndex,
                content,
                "hash-" + chunkIndex,
                chunkIndex * 10,
                chunkIndex * 10 + content.length(),
                5,
                "mock-model"
        );
    }
}
