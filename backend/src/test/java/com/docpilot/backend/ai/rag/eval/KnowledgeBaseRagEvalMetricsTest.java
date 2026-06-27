package com.docpilot.backend.ai.rag.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseRagEvalMetricsTest {

    @Test
    void shouldCalculateKnowledgeBaseRagMetrics() {
        List<KnowledgeBaseRagEvalResult.CaseEvaluation> evaluations = List.of(
                evaluation("hit", false, true, true, true, false, false),
                evaluation("citation-miss", false, true, true, false, false, false),
                evaluation("no-evidence", true, false, false, false, true, false),
                evaluation("scope-violation", false, false, false, false, false, true)
        );

        KnowledgeBaseRagEvalMetrics metrics = KnowledgeBaseRagEvalMetrics.from(evaluations);

        assertThat(metrics.caseCount()).isEqualTo(4);
        assertThat(metrics.hitAtK()).isEqualTo(2.0D / 3.0D);
        assertThat(metrics.documentHitRate()).isEqualTo(2.0D / 3.0D);
        assertThat(metrics.citationHitRate()).isEqualTo(1.0D / 3.0D);
        assertThat(metrics.answerHitRate()).isEqualTo(2.0D / 3.0D);
        assertThat(metrics.citationCountRate()).isEqualTo(2.0D / 3.0D);
        assertThat(metrics.multiDocumentCoverageRate()).isEqualTo(1.0D);
        assertThat(metrics.forbiddenAnswerLeakRate()).isEqualTo(0.0D);
        assertThat(metrics.noEvidenceRate()).isEqualTo(1.0D);
        assertThat(metrics.scopeViolationRate()).isEqualTo(0.25D);
        assertThat(metrics.toSafeMap()).containsEntry("scopeViolationRate", "0.2500");
    }

    @Test
    void shouldUseSafeDefaultsForEmptyEvaluationSet() {
        KnowledgeBaseRagEvalMetrics metrics = KnowledgeBaseRagEvalMetrics.from(List.of());

        assertThat(metrics.caseCount()).isZero();
        assertThat(metrics.hitAtK()).isEqualTo(1.0D);
        assertThat(metrics.documentHitRate()).isEqualTo(1.0D);
        assertThat(metrics.citationHitRate()).isEqualTo(1.0D);
        assertThat(metrics.answerHitRate()).isEqualTo(1.0D);
        assertThat(metrics.citationCountRate()).isEqualTo(1.0D);
        assertThat(metrics.multiDocumentCoverageRate()).isEqualTo(1.0D);
        assertThat(metrics.forbiddenAnswerLeakRate()).isEqualTo(0.0D);
        assertThat(metrics.noEvidenceRate()).isEqualTo(1.0D);
        assertThat(metrics.scopeViolationRate()).isEqualTo(0.0D);
    }

    private KnowledgeBaseRagEvalResult.CaseEvaluation evaluation(String id,
                                                                 boolean expectedNoEvidence,
                                                                 boolean hit,
                                                                 boolean documentHit,
                                                                 boolean citationHit,
                                                                 boolean noEvidenceHit,
                                                                 boolean scopeViolation) {
        return new KnowledgeBaseRagEvalResult.CaseEvaluation(
                id,
                expectedNoEvidence,
                hit ? 1 : 0,
                citationHit ? 1 : 0,
                List.of(1L),
                List.of(1L),
                List.of(1L),
                hit,
                documentHit,
                citationHit,
                hit,
                false,
                citationHit || "citation-miss".equals(id),
                id.contains("hit"),
                id.contains("hit"),
                noEvidenceHit,
                scopeViolation,
                false,
                !scopeViolation,
                "",
                List.of()
        );
    }
}
