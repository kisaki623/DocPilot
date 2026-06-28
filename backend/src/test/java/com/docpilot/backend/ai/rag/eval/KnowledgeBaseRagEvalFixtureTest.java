package com.docpilot.backend.ai.rag.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseRagEvalFixtureTest {

    @Test
    void shouldLoadKnowledgeBaseRagEvalCases() throws Exception {
        List<KnowledgeBaseRagEvalCase> cases = new KnowledgeBaseRagEvalRunner().loadCases();

        assertThat(cases).hasSizeGreaterThanOrEqualTo(11);
        assertThat(cases).allSatisfy(evalCase -> {
            assertThat(evalCase.id()).isNotBlank();
            assertThat(evalCase.userId()).isPositive();
            assertThat(evalCase.knowledgeBaseId()).isPositive();
            assertThat(evalCase.indexVersion()).isPositive();
            assertThat(evalCase.topK()).isPositive();
            assertThat(evalCase.query()).isNotBlank();
            assertThat(evalCase.minCitationCount()).isNotNegative();
            assertThat(evalCase.minSimilarityThreshold()).isBetween(0.0D, 1.0D);
        });
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.id()).contains("multi-document"));
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.expectedNoEvidence()).isTrue());
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.outOfScopeDocuments()).isNotEmpty());
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.expectedDocumentIds()).hasSizeGreaterThan(1));
        assertThat(cases).anySatisfy(evalCase -> {
            assertThat(evalCase.requiresMultiDocumentCoverage()).isTrue();
            assertThat(evalCase.minCitationCount()).isGreaterThanOrEqualTo(2);
        });
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.expectedAnswerMarkers()).isNotEmpty());
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.forbiddenAnswerMarkers()).isNotEmpty());
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.id()).contains("citation"));
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.id()).isEqualTo("semantic-no-evidence-populated-kb"));
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.id()).isEqualTo("hybrid-keyword-noise-no-evidence"));
        assertThat(cases).anySatisfy(evalCase -> {
            assertThat(evalCase.id()).isEqualTo("semantic-no-evidence-populated-kb");
            assertThat(evalCase.minSimilarityThreshold()).isEqualTo(0.5D);
        });
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.id()).isEqualTo("multi-document-three-way-summary"));
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.id()).isEqualTo("grounded-answer-distractor-suppression"));
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.id()).isEqualTo("cross-topic-distractor-routing"));
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.id()).isEqualTo("out-of-scope-semantic-distractor"));
    }
}
