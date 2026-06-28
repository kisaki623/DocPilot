package com.docpilot.backend.memory.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryQualityEvalFixtureTest {

    @Test
    void shouldLoadMemoryQualityEvalCases() throws Exception {
        List<MemoryQualityEvalCase> cases = new MemoryQualityEvalRunner().loadCases();

        assertThat(cases).hasSizeGreaterThanOrEqualTo(5);
        assertThat(cases).allSatisfy(evalCase -> {
            assertThat(evalCase.id()).isNotBlank();
            assertThat(evalCase.category()).isNotBlank();
            assertThat(evalCase.userId()).isPositive();
            assertThat(evalCase.conversationId()).isPositive();
            assertThat(evalCase.currentMessage()).isNotBlank();
            assertThat(evalCase.expectedTraceCounts()).containsKeys(
                    "conversationSummary", "recentMessages", "userMemory", "ragEvidence");
        });
        assertThat(cases).extracting(MemoryQualityEvalCase::category).contains(
                "preference_extraction",
                "rag_evidence_isolation",
                "memory_status_isolation",
                "safety_filter",
                "trace_source_split"
        );
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.expectSensitiveRejected()).isTrue());
        assertThat(cases).anySatisfy(evalCase -> assertThat(evalCase.ragEvidenceCount()).isGreaterThan(0));
    }
}
