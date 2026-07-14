package com.docpilot.backend.ai.rag.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagRealQaEvalFixtureTest {

    @Test
    void shouldLoadRealQaEvalCases() throws Exception {
        List<RagRealQaEvalCase> cases = new RagRealQaEvalRunner().loadCases();

        assertThat(cases).hasSizeGreaterThanOrEqualTo(40);
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
                "rerank_uplift_candidate",
                "long_document",
                "near_miss_no_evidence",
                "multi_doc_summary",
                "citation_grounding",
                "scope_isolation",
                "hard_negative",
                "answer_faithfulness",
                "claim_support",
                "numeric_faithfulness"
        );
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.expectedNoEvidence()).isTrue());
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.minDocumentCoverage()).isGreaterThanOrEqualTo(3));
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.rerankUpliftCandidate()).isTrue());
        assertThat(cases).filteredOn(evalCase -> "long_document".equals(evalCase.category())).hasSizeGreaterThanOrEqualTo(3);
        assertThat(cases).filteredOn(evalCase -> "near_miss_no_evidence".equals(evalCase.category())).hasSizeGreaterThanOrEqualTo(2);
        assertThat(cases).filteredOn(evalCase -> "hard_negative".equals(evalCase.category())).hasSizeGreaterThanOrEqualTo(2);
        assertThat(cases).filteredOn(evalCase -> "answer_faithfulness".equals(evalCase.category())).hasSizeGreaterThanOrEqualTo(2);
        assertThat(cases).filteredOn(evalCase -> "claim_support".equals(evalCase.category())).hasSizeGreaterThanOrEqualTo(2);
        assertThat(cases).filteredOn(evalCase -> "numeric_faithfulness".equals(evalCase.category())).hasSizeGreaterThanOrEqualTo(3);
        assertThat(cases).filteredOn(evalCase -> "multi_doc_summary".equals(evalCase.category())).hasSizeGreaterThanOrEqualTo(2);
        assertThat(cases).filteredOn(evalCase -> "scope_isolation".equals(evalCase.category())).hasSizeGreaterThanOrEqualTo(2);
        assertThat(cases).filteredOn(evalCase -> !evalCase.expectedClaims().isEmpty()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(cases).filteredOn(evalCase -> !evalCase.expectedClaims().isEmpty())
                .allSatisfy(evalCase -> assertThat(evalCase.expectedClaims())
                        .allSatisfy(claim -> {
                            assertThat(claim.id()).isNotBlank();
                            assertThat(claim.answerMarkers()).isNotEmpty();
                            assertThat(claim.evidenceMarkers()).isNotEmpty();
                        }));
    }
}
