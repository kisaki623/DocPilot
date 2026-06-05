package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseRagPromptBuilderTest {

    private final KnowledgeBaseRagPromptBuilder builder = new KnowledgeBaseRagPromptBuilder();

    @Test
    void shouldBuildEvidenceContextWithDocumentTitlesAndAlignedCitationNumbers() {
        RagPrompt prompt = builder.build("What is cached?", List.of(
                hit(1, 101L, "Redis Guide", "Redis stores QA answers."),
                hit(2, 102L, "Search Guide", "Qdrant stores vectors.")
        ), 2000);

        assertThat(prompt.noEvidence()).isFalse();
        assertThat(prompt.evidenceContext()).contains("[1] documentId=101, title=Redis Guide");
        assertThat(prompt.evidenceContext()).contains("[2] documentId=102, title=Search Guide");
        assertThat(prompt.userPrompt()).contains("only the numbered knowledge-base evidence");
    }

    @Test
    void shouldNotRenumberWhenContextIsTruncated() {
        RagPrompt prompt = builder.build("question", List.of(
                hit(1, 101L, "Doc A", "first evidence block with long content"),
                hit(2, 102L, "Doc B", "second evidence block")
        ), 120);

        assertThat(prompt.evidenceContext()).contains("[1] documentId=101");
        assertThat(prompt.evidenceContext()).doesNotContain("[2] documentId=102");
    }

    @Test
    void shouldReturnNoEvidencePromptWithoutContext() {
        RagPrompt prompt = builder.build("question", List.of(), 100);

        assertThat(prompt.noEvidence()).isTrue();
        assertThat(prompt.evidenceContext()).isBlank();
        assertThat(prompt.userPrompt()).contains("No evidence was retrieved");
    }

    private KnowledgeBaseRagRetrievalHit hit(int index, Long documentId, String title, String content) {
        return new KnowledgeBaseRagRetrievalHit(
                index,
                10L,
                "v" + index,
                0.9D,
                7L,
                documentId,
                title,
                1,
                900L + index,
                index - 1,
                content,
                "hash-" + index,
                0,
                content.length(),
                5,
                "mock-model"
        );
    }
}
