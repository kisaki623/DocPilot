package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaAnswer;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.exception.AiNonRetryableException;
import com.docpilot.backend.ai.exception.AiRetryableException;
import com.docpilot.backend.ai.service.impl.AiRetryExecutor;
import com.docpilot.backend.ai.service.impl.KnowledgeBaseRagQaServiceImpl;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseRagQaServiceImplTest {

    private final KnowledgeBaseRagRetrievalService retrievalService = mock(KnowledgeBaseRagRetrievalService.class);
    private final AiAnswerService aiAnswerService = mock(AiAnswerService.class);
    private final RagQaProperties ragQaProperties = new RagQaProperties();
    private final KnowledgeBaseRagQaServiceImpl service = new KnowledgeBaseRagQaServiceImpl(
            retrievalService,
            aiAnswerService,
            ragQaProperties
    );

    @Test
    void shouldAnswerWithEvidenceAndModelMetadata() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(retrieval(false));
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn("Use Redis [1].");
        when(aiAnswerService.provider()).thenReturn("mock");
        when(aiAnswerService.model()).thenReturn("mock-model");

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "cache?",
                3,
                1,
                "s1"
        ));

        assertThat(answer.answer()).isEqualTo("Use Redis [1].");
        assertThat(answer.noEvidence()).isFalse();
        assertThat(answer.retrieval().citations()).hasSize(1);
        assertThat(answer.answerProvider()).isEqualTo("mock");
        assertThat(answer.answerModel()).isEqualTo("mock-model");
        assertThat(answer.modelCallCount()).isEqualTo(1);
        assertThat(answer.audit().grounded()).isTrue();
        assertThat(answer.audit().evidenceCount()).isEqualTo(1);
        assertThat(answer.audit().citationCount()).isEqualTo(1);
        assertThat(answer.audit().scoreSummary().min()).isEqualTo(0.9D);
        assertThat(answer.audit().documentHitCounts()).containsEntry(101L, 1);
    }

    @Test
    void shouldNotCallModelWhenNoEvidence() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(retrieval(true));

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "missing?",
                3,
                1,
                ""
        ));

        assertThat(answer.noEvidence()).isTrue();
        assertThat(answer.answer()).contains("未在当前知识库索引中检索到足够证据");
        assertThat(answer.modelCallCount()).isZero();
        assertThat(answer.audit().grounded()).isFalse();
        assertThat(answer.audit().citationCount()).isZero();
        assertThat(answer.audit().fallbackReason()).isEqualTo("no_evidence");
        verify(aiAnswerService, never()).answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString());
    }

    @Test
    void shouldFallbackWithoutCallingModelWhenRetrievalFails() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenThrow(new IllegalStateException("vector down"));

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "question",
                3,
                1,
                ""
        ));

        assertThat(answer.fallbackUsed()).isTrue();
        assertThat(answer.fallbackReason()).isEqualTo("retrieval_unavailable");
        assertThat(answer.modelCallCount()).isZero();
        assertThat(answer.audit().grounded()).isFalse();
        assertThat(answer.audit().evidenceCount()).isZero();
        assertThat(answer.audit().fallbackReason()).isEqualTo("retrieval_unavailable");
        verify(aiAnswerService, never()).answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString());
    }

    @Test
    void shouldFallbackWithEvidenceWhenAnswerGenerationFails() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(retrieval(false));
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenThrow(new AiNonRetryableException("invalid request"));
        when(aiAnswerService.provider()).thenReturn("real");
        when(aiAnswerService.model()).thenReturn("real-model");

        KnowledgeBaseRagQaServiceImpl retryEnabledService = serviceWithRetry();
        KnowledgeBaseRagQaAnswer answer = retryEnabledService.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "summary?",
                3,
                1,
                "s1"
        ));

        assertThat(answer.noEvidence()).isFalse();
        assertThat(answer.fallbackUsed()).isTrue();
        assertThat(answer.fallbackReason()).isEqualTo("answer_generation_failed");
        assertThat(answer.answer()).contains("回答模型本次生成失败");
        assertThat(answer.retrieval().citations()).hasSize(1);
        assertThat(answer.answerProvider()).isEqualTo("real");
        assertThat(answer.answerModel()).isEqualTo("real-model");
        assertThat(answer.modelCallCount()).isEqualTo(1);
        assertThat(answer.audit().grounded()).isFalse();
        assertThat(answer.audit().citationCount()).isEqualTo(1);
        assertThat(answer.audit().fallbackReason()).isEqualTo("answer_generation_failed");
        verify(aiAnswerService, times(1)).answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString());
    }

    @Test
    void shouldRetryRetryableModelFailureAndReportActualModelAttemptCount() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(retrieval(false));
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenThrow(new AiRetryableException("temporary failure"))
                .thenReturn("Use Redis [1].");
        KnowledgeBaseRagQaServiceImpl retryEnabledService = serviceWithRetry();

        KnowledgeBaseRagQaAnswer answer = retryEnabledService.answer(new KnowledgeBaseRagQaQuery(
                7L, 10L, "cache?", 3, 1, "s1"
        ));

        assertThat(answer.answer()).isEqualTo("Use Redis [1].");
        assertThat(answer.fallbackUsed()).isFalse();
        assertThat(answer.modelCallCount()).isEqualTo(2);
        verify(retrievalService, times(1)).retrieve(org.mockito.Mockito.any());
        verify(aiAnswerService, times(2)).answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString());
    }

    @Test
    void shouldFallbackAfterRetryExhaustionAndReportAllModelAttempts() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(retrieval(false));
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenThrow(new AiRetryableException("temporary failure"));
        KnowledgeBaseRagQaServiceImpl retryEnabledService = serviceWithRetry();

        KnowledgeBaseRagQaAnswer answer = retryEnabledService.answer(new KnowledgeBaseRagQaQuery(
                7L, 10L, "cache?", 3, 1, "s1"
        ));

        assertThat(answer.fallbackUsed()).isTrue();
        assertThat(answer.fallbackReason()).isEqualTo("answer_generation_failed");
        assertThat(answer.modelCallCount()).isEqualTo(3);
        assertThat(answer.retrieval().citations()).hasSize(1);
        verify(retrievalService, times(1)).retrieve(org.mockito.Mockito.any());
        verify(aiAnswerService, times(3)).answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString());
    }

    @Test
    void shouldNotMaskScopeExceptionWithFallback() {
        when(retrievalService.retrieve(org.mockito.Mockito.any()))
                .thenThrow(new BusinessException(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "question",
                3,
                1,
                ""
        )));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void shouldPassMultiQueryOverrideToRetrieval() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(retrieval(true));

        service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "compare cache and vector retention",
                4,
                1,
                "s1",
                true,
                4
        ));

        org.mockito.ArgumentCaptor<KnowledgeBaseRagRetrievalQuery> captor =
                org.mockito.ArgumentCaptor.forClass(KnowledgeBaseRagRetrievalQuery.class);
        verify(retrievalService).retrieve(captor.capture());
        assertThat(captor.getValue().multiQueryEnabled()).isTrue();
        assertThat(captor.getValue().maxQueryVariants()).isEqualTo(4);
    }

    @Test
    void shouldFilterNumericDistractorCitationsAfterAnswerGeneration() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(numericDistractorRetrieval());
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn("Invoice archive retention is 7 years [1].");

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "How long are invoice archive records retained?",
                2,
                1,
                "s1"
        ));

        assertThat(answer.retrieval().hits()).hasSize(2);
        assertThat(answer.retrieval().citations()).hasSize(1);
        assertThat(answer.retrieval().citations().get(0).documentId()).isEqualTo(101L);
        assertThat(answer.retrieval().citations().get(0).quoteText()).contains("7 years");
        assertThat(answer.audit().citationCount()).isEqualTo(1);
        assertThat(answer.audit().documentHitCounts()).containsEntry(101L, 1).containsEntry(102L, 1);
    }

    @Test
    void shouldStillFilterNumericDistractorForSingleFactQuestionWithTwoDocumentWording() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(numericDistractorRetrieval());
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn("Invoice archive retention is 7 years [1].");

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "在两个文档中，invoice archive records retained 多久？",
                2,
                1,
                "s1"
        ));

        assertThat(answer.retrieval().citations()).hasSize(1);
        assertThat(answer.retrieval().citations().get(0).documentId()).isEqualTo(101L);
        assertThat(answer.retrieval().citations().get(0).quoteText()).contains("7 years");
    }

    @Test
    void shouldKeepNonNumericSupportCitationWhenChunkContainsRunMarkerDigits() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(multiDocumentRetrievalWithRunMarker());
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn("The team paused background retries, and the P1 response target is 30 minutes [1][2].");

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "Summarize the checkout incident response and support SLA.",
                2,
                1,
                "s1"
        ));

        assertThat(answer.retrieval().citations()).hasSize(2);
        assertThat(answer.retrieval().citations()).extracting("documentId")
                .containsExactly(101L, 102L);
    }

    @Test
    void shouldDropLowScoreSummaryDistractorAfterPreservingTargetCoverage() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(multiDocumentSummaryRetrievalWithDistractor());
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn("The checkout response paused background retries, and the P1 response target is 30 minutes [1][2].");

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "Summarize the checkout worker queue incident response and the P1 response target from customer support SLA.",
                4,
                1,
                "s1"
        ));

        assertThat(answer.retrieval().hits()).hasSize(3);
        assertThat(answer.retrieval().citations()).hasSize(2);
        assertThat(answer.retrieval().citations()).extracting("documentId")
                .containsExactly(101L, 102L);
        assertThat(answer.audit().citationCount()).isEqualTo(2);
        assertThat(answer.audit().documentHitCounts()).containsEntry(101L, 1)
                .containsEntry(102L, 1)
                .containsEntry(103L, 1);
    }

    @Test
    void shouldKeepMultiDocumentCitationsForCompareEvenWhenOnlyOneNumberAppearsInAnswer() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(multiDocumentNumericCompareRetrieval());
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn("The SRE on-call lead owns restore drills, while the release captain can roll back within 15 minutes [1][2].");

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "Compare backup verification ownership with feature rollback authority.",
                2,
                1,
                "s1"
        ));

        assertThat(answer.retrieval().citations()).hasSize(2);
        assertThat(answer.retrieval().citations()).extracting("documentId")
                .containsExactly(101L, 102L);
    }

    @Test
    void shouldKeepApprovalCitationForChineseMultiDocumentQuestionWhenAnswerHasOnlyContractNumber() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(fixedCorpusApprovalRetrieval());
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn("超过 50 万元的合同需要法务和财务共同审批；管理员权限变更需要两名审批人共同确认 [1][2].");

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "哪些场景需要多人审批？分别出现在什么文档中？",
                2,
                1,
                "s1"
        ));

        assertThat(answer.retrieval().citations()).hasSize(2);
        assertThat(answer.retrieval().citations()).extracting("documentId")
                .containsExactly(101L, 102L);
    }

    @Test
    void shouldKeepComprehensiveRiskControlCitationsWhenAnswerMentionsOnlySomeNumbers() {
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(fixedCorpusRiskControlsRetrieval());
        when(aiAnswerService.answer(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn("四项控制包括合同审批、API 密钥 90 天轮换、审计治理，以及连接池隔离、限流和紧急回滚 [1][2][3].");

        KnowledgeBaseRagQaAnswer answer = service.answer(new KnowledgeBaseRagQaQuery(
                7L,
                10L,
                "综合合同、安全规范和事故复盘，总结当前系统需要落实的四项风险控制措施。",
                3,
                1,
                "s1"
        ));

        assertThat(answer.retrieval().citations()).hasSize(3);
        assertThat(answer.retrieval().citations()).extracting("documentId")
                .containsExactly(101L, 102L, 103L);
    }

    private KnowledgeBaseRagRetrievalResult retrieval(boolean noEvidence) {
        List<KnowledgeBaseRagRetrievalHit> hits = noEvidence ? List.of() : List.of(new KnowledgeBaseRagRetrievalHit(
                1,
                10L,
                "v1",
                0.9D,
                7L,
                101L,
                "Redis Guide",
                1,
                900L,
                0,
                "Redis stores cache.",
                "hash",
                0,
                19,
                4,
                "mock-model"
        ));
        return new KnowledgeBaseRagRetrievalResult(
                7L,
                10L,
                "question",
                3,
                1,
                List.of(101L),
                hits,
                hits.stream().map(KnowledgeBaseRagRetrievalHit::toCitation).toList(),
                noEvidence,
                "in_memory",
                "",
                "mock-model",
                Map.of(101L, hits.size())
        );
    }

    private KnowledgeBaseRagQaServiceImpl serviceWithRetry() {
        AiRetryExecutor retryExecutor = new AiRetryExecutor();
        ReflectionTestUtils.setField(retryExecutor, "retryEnabled", true);
        ReflectionTestUtils.setField(retryExecutor, "maxAttempts", 3);
        ReflectionTestUtils.setField(retryExecutor, "initialBackoffMs", 1L);
        ReflectionTestUtils.setField(retryExecutor, "backoffMultiplier", 2.0D);
        ReflectionTestUtils.setField(retryExecutor, "maxBackoffMs", 10L);
        return new KnowledgeBaseRagQaServiceImpl(retrievalService, aiAnswerService, ragQaProperties, retryExecutor);
    }

    private KnowledgeBaseRagRetrievalResult numericDistractorRetrieval() {
        List<KnowledgeBaseRagRetrievalHit> hits = List.of(
                new KnowledgeBaseRagRetrievalHit(
                        1,
                        10L,
                        "invoice",
                        0.99D,
                        7L,
                        101L,
                        "Invoice Retention Policy",
                        1,
                        901L,
                        0,
                        "Invoice archive retention is 7 years. The archive owner is Finance Operations.",
                        "hash-invoice",
                        0,
                        80,
                        80,
                        "mock-model"
                ),
                new KnowledgeBaseRagRetrievalHit(
                        2,
                        10L,
                        "marketing",
                        0.88D,
                        7L,
                        102L,
                        "Marketing Draft Retention",
                        1,
                        902L,
                        0,
                        "Marketing campaign drafts are retained for 3 years. This document should not be used as invoice archive evidence.",
                        "hash-marketing",
                        0,
                        112,
                        112,
                        "mock-model"
                )
        );
        return new KnowledgeBaseRagRetrievalResult(
                7L,
                10L,
                "How long are invoice archive records retained?",
                2,
                1,
                List.of(101L, 102L),
                hits,
                hits.stream().map(KnowledgeBaseRagRetrievalHit::toCitation).toList(),
                false,
                "in_memory",
                "",
                "mock-model",
                Map.of(101L, 1, 102L, 1)
        );
    }

    private KnowledgeBaseRagRetrievalResult multiDocumentRetrievalWithRunMarker() {
        List<KnowledgeBaseRagRetrievalHit> hits = List.of(
                new KnowledgeBaseRagRetrievalHit(
                        1,
                        10L,
                        "incident",
                        0.96D,
                        7L,
                        101L,
                        "Checkout Incident Review",
                        1,
                        903L,
                        0,
                        "docpilot-rag-natural-corpus-20260704142549-252f85. Engineers paused background retries and drained the checkout worker queue.",
                        "hash-incident",
                        0,
                        130,
                        130,
                        "mock-model"
                ),
                new KnowledgeBaseRagRetrievalHit(
                        2,
                        10L,
                        "support",
                        0.95D,
                        7L,
                        102L,
                        "Support SLA Note",
                        1,
                        904L,
                        0,
                        "The P1 response target is 30 minutes when checkout cannot complete.",
                        "hash-support",
                        0,
                        72,
                        72,
                        "mock-model"
                )
        );
        return new KnowledgeBaseRagRetrievalResult(
                7L,
                10L,
                "Summarize the checkout incident response and support SLA.",
                2,
                1,
                List.of(101L, 102L),
                hits,
                hits.stream().map(KnowledgeBaseRagRetrievalHit::toCitation).toList(),
                false,
                "in_memory",
                "",
                "mock-model",
                Map.of(101L, 1, 102L, 1)
        );
    }

    private KnowledgeBaseRagRetrievalResult multiDocumentSummaryRetrievalWithDistractor() {
        List<KnowledgeBaseRagRetrievalHit> hits = List.of(
                new KnowledgeBaseRagRetrievalHit(
                        1,
                        10L,
                        "incident",
                        0.999D,
                        7L,
                        101L,
                        "Checkout Incident Review",
                        1,
                        905L,
                        0,
                        "Engineers paused background retries and drained the checkout worker queue.",
                        "hash-incident",
                        0,
                        77,
                        77,
                        "mock-model"
                ),
                new KnowledgeBaseRagRetrievalHit(
                        2,
                        10L,
                        "support",
                        0.998D,
                        7L,
                        102L,
                        "Customer Support SLA",
                        1,
                        906L,
                        0,
                        "The P1 response target is 30 minutes when checkout cannot complete.",
                        "hash-support",
                        0,
                        72,
                        72,
                        "mock-model"
                ),
                new KnowledgeBaseRagRetrievalHit(
                        3,
                        10L,
                        "backup",
                        0.0007D,
                        7L,
                        103L,
                        "Backup Rotation Note",
                        1,
                        907L,
                        0,
                        "The backup rotation note describes weekly restore verification and should not support checkout SLA summary.",
                        "hash-backup",
                        0,
                        101,
                        101,
                        "mock-model"
                )
        );
        return new KnowledgeBaseRagRetrievalResult(
                7L,
                10L,
                "Summarize the checkout worker queue incident response and the P1 response target from customer support SLA.",
                4,
                1,
                List.of(101L, 102L, 103L),
                hits,
                hits.stream().map(KnowledgeBaseRagRetrievalHit::toCitation).toList(),
                false,
                "in_memory",
                "",
                "mock-model",
                Map.of(101L, 1, 102L, 1, 103L, 1)
        );
    }

    private KnowledgeBaseRagRetrievalResult multiDocumentNumericCompareRetrieval() {
        List<KnowledgeBaseRagRetrievalHit> hits = List.of(
                new KnowledgeBaseRagRetrievalHit(
                        1,
                        10L,
                        "backup",
                        0.94D,
                        7L,
                        101L,
                        "Database Backup Runbook",
                        1,
                        905L,
                        0,
                        "Database backup verification runs every 14 days. The restore drill owner is the SRE on-call lead.",
                        "hash-backup",
                        0,
                        98,
                        98,
                        "mock-model"
                ),
                new KnowledgeBaseRagRetrievalHit(
                        2,
                        10L,
                        "rollback",
                        0.93D,
                        7L,
                        102L,
                        "Feature Rollback Runbook",
                        1,
                        906L,
                        0,
                        "The release captain can trigger feature flag rollback within 15 minutes.",
                        "hash-rollback",
                        0,
                        74,
                        74,
                        "mock-model"
                )
        );
        return new KnowledgeBaseRagRetrievalResult(
                7L,
                10L,
                "Compare backup verification ownership with feature rollback authority.",
                2,
                1,
                List.of(101L, 102L),
                hits,
                hits.stream().map(KnowledgeBaseRagRetrievalHit::toCitation).toList(),
                false,
                "in_memory",
                "",
                "mock-model",
                Map.of(101L, 1, 102L, 1)
        );
    }

    private KnowledgeBaseRagRetrievalResult fixedCorpusApprovalRetrieval() {
        List<KnowledgeBaseRagRetrievalHit> hits = List.of(
                hit(1, 101L, "contract", "Contract Alpha",
                        "合同金额超过 50 万元时，需要法务和财务共同审批。", 0.96D),
                hit(2, 102L, "api", "API Policy",
                        "API 密钥必须每 90 天轮换一次。管理员权限变更需要两名审批人共同确认。审计日志应保留 180 天。", 0.95D)
        );
        return retrieval("哪些场景需要多人审批？分别出现在什么文档中？", List.of(101L, 102L), hits);
    }

    private KnowledgeBaseRagRetrievalResult fixedCorpusRiskControlsRetrieval() {
        List<KnowledgeBaseRagRetrievalHit> hits = List.of(
                hit(1, 101L, "contract", "Contract Alpha",
                        "合同金额超过 50 万元时，需要法务和财务共同审批。合同、验收单和付款凭证应保留 24 个月。", 0.97D),
                hit(2, 102L, "api", "API Policy",
                        "API 密钥必须每 90 天轮换一次。禁止在日志、数据库和代码仓库中明文记录访问 Token。审计日志应保留 180 天。", 0.96D),
                hit(3, 103L, "incident", "P1 Incident Review",
                        "根因是缓存预热任务占满数据库连接池。改进措施包括连接池隔离、请求限流和紧急回滚开关。", 0.95D)
        );
        return retrieval("综合合同、安全规范和事故复盘，总结当前系统需要落实的四项风险控制措施。",
                List.of(101L, 102L, 103L),
                hits);
    }

    private KnowledgeBaseRagRetrievalResult retrieval(String question,
                                                      List<Long> documentIds,
                                                      List<KnowledgeBaseRagRetrievalHit> hits) {
        return new KnowledgeBaseRagRetrievalResult(
                7L,
                10L,
                question,
                hits.size(),
                1,
                documentIds,
                hits,
                hits.stream().map(KnowledgeBaseRagRetrievalHit::toCitation).toList(),
                false,
                "in_memory",
                "",
                "mock-model",
                documentIds.stream().collect(java.util.stream.Collectors.toMap(
                        id -> id,
                        id -> (int) hits.stream().filter(hit -> id.equals(hit.documentId())).count()
                ))
        );
    }

    private KnowledgeBaseRagRetrievalHit hit(int index,
                                             Long documentId,
                                             String vectorId,
                                             String title,
                                             String content,
                                             double score) {
        return new KnowledgeBaseRagRetrievalHit(
                index,
                10L,
                vectorId,
                score,
                7L,
                documentId,
                title,
                1,
                900L + index,
                index - 1,
                content,
                "hash-" + vectorId,
                0,
                content.length(),
                content.length(),
                "mock-model"
        );
    }
}
