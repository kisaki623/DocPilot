package com.docpilot.backend.ai.rag.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagRealQaEvalFixtureTest {

    @Test
    void shouldLoadRealQaEvalCases() throws Exception {
        List<RagRealQaEvalCase> cases = new RagRealQaEvalRunner().loadCases();

        assertThat(cases).hasSizeGreaterThanOrEqualTo(8);
        assertThat(cases).allSatisfy(evalCase -> {
            assertThat(evalCase.id()).isNotBlank();
            assertThat(evalCase.category()).isNotBlank();
            assertThat(evalCase.userId()).isPositive();
            assertThat(evalCase.knowledgeBaseId()).isPositive();
            assertThat(evalCase.query()).isNotBlank();
            assertThat(evalCase.minSimilarityThreshold()).isBetween(0.0D, 1.0D);
        });
        assertThat(cases).extracting(RagRealQaEvalCase::category).contains(
                "factual_lookup",
                "cross_document_summary",
                "comparison",
                "multi_hop",
                "no_evidence",
                "semantic_distractor",
                "hybrid_keyword_noise",
                "rerank_uplift_candidate"
        );
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.expectedNoEvidence()).isTrue());
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.minDocumentCoverage()).isGreaterThanOrEqualTo(3));
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.rerankUpliftCandidate()).isTrue());
    }
}
