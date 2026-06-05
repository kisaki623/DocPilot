package com.docpilot.backend.ai.rag.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseRagEvalFixtureTest {

    @Test
    void shouldLoadKnowledgeBaseRagEvalCases() throws Exception {
        List<KnowledgeBaseRagEvalCase> cases = new KnowledgeBaseRagEvalRunner().loadCases();

        assertThat(cases).hasSizeGreaterThanOrEqualTo(5);
        assertThat(cases).allSatisfy(evalCase -> {
            assertThat(evalCase.id()).isNotBlank();
            assertThat(evalCase.userId()).isPositive();
            assertThat(evalCase.knowledgeBaseId()).isPositive();
            assertThat(evalCase.indexVersion()).isPositive();
            assertThat(evalCase.topK()).isPositive();
            assertThat(evalCase.query()).isNotBlank();
        });
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.id()).contains("multi-document"));
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.expectedNoEvidence()).isTrue());
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.outOfScopeDocuments()).isNotEmpty());
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.expectedDocumentIds()).hasSizeGreaterThan(1));
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.id()).contains("citation"));
    }
}
