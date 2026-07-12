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

    @Test
    void shouldUseCorpusSummaryPromptForKnowledgeBaseSummaryQuestions() {
        RagPrompt prompt = builder.build("请你阅读资料集，帮我总结一下资料及里面文档的内容", List.of(
                hit(1, 101L, "harness.md", "Harness document content."),
                hit(2, 102L, "MCP.md", "MCP document content.")
        ), 2000);

        assertThat(prompt.noEvidence()).isFalse();
        assertThat(prompt.userPrompt()).contains("overview of the whole knowledge base or dataset");
        assertThat(prompt.userPrompt()).contains("summarize the covered documents by title");
        assertThat(prompt.userPrompt()).contains("Do not skip any represented title or documentId");
        assertThat(prompt.evidenceContext()).contains("title=harness.md");
    }

    @Test
    void shouldRequireItemizedCoverageForRiskControlSummaryQuestions() {
        RagPrompt prompt = builder.build("综合合同、安全规范和事故复盘，总结当前系统需要落实的四项风险控制措施。", List.of(
                hit(1, 101L, "Contract Alpha", "合同金额超过 50 万元时，需要法务和财务共同审批。"),
                hit(2, 102L, "API Policy", "API 密钥必须每 90 天轮换一次。禁止在日志、数据库和代码仓库中明文记录访问 Token。"),
                hit(3, 103L, "Incident Review", "改进措施包括连接池隔离、请求限流和紧急回滚开关。")
        ), 2000);

        assertThat(prompt.noEvidence()).isFalse();
        assertThat(prompt.userPrompt())
                .contains("specific number of items")
                .contains("every represented document")
                .contains("risk-control or control-measure")
                .contains("credential or token controls")
                .contains("operational mitigations");
    }

    @Test
    void shouldUseCorrectionPromptForFalsePremiseQuestions() {
        RagPrompt prompt = builder.build("合同规定违约金是每天 1%，对吗？", List.of(
                hit(1, 101L, "Contract Alpha", "违约金按照未付款金额的 0.3% 每日计算，累计最高不超过 8%。"),
                hit(2, 102L, "Decoy Draft", "以下内容均为被否决的旧方案：违约金为每日 1%。")
        ), 2000);

        assertThat(prompt.noEvidence()).isFalse();
        assertThat(prompt.userPrompt())
                .contains("proposed value")
                .contains("directly related caps")
                .contains("obsolete drafts");
    }

    @Test
    void shouldNotUseCorrectionPromptForSlaCalculationQuestions() {
        RagPrompt prompt = builder.build("SLA 要求 P1 故障两小时内恢复。本次事故恢复用了 78 分钟，是否达到 SLA？", List.of(
                hit(1, 101L, "SLA Beta", "P1 故障要求 10 分钟内响应，2 小时内恢复。"),
                hit(2, 102L, "Incident Review", "本次故障从确认到恢复共耗时 78 分钟。")
        ), 2000);

        assertThat(prompt.noEvidence()).isFalse();
        assertThat(prompt.userPrompt()).contains("only the numbered knowledge-base evidence");
        assertThat(prompt.userPrompt()).doesNotContain("proposed value");
    }

    @Test
    void shouldPreferCorrectionPromptWhenKnowledgeBaseFalsePremiseMentionsKnowledgeBase() {
        RagPrompt prompt = builder.build("知识库里说合同违约金是每天 1%，对吗？", List.of(
                hit(1, 101L, "Contract Alpha", "违约金按照未付款金额的 0.3% 每日计算，累计最高不超过 8%。"),
                hit(2, 102L, "Decoy Draft", "以下内容均为被否决的旧方案：违约金为每日 1%。")
        ), 2000);

        assertThat(prompt.noEvidence()).isFalse();
        assertThat(prompt.userPrompt()).contains("proposed value");
        assertThat(prompt.userPrompt()).doesNotContain("overview of the whole knowledge base");
    }

    @Test
    void shouldNotUseCorrectionPromptForEnglishWhetherCalculationQuestions() {
        RagPrompt prompt = builder.build("Whether the 78-minute recovery met the two-hour SLA?", List.of(
                hit(1, 101L, "SLA Beta", "P1 incidents must recover within 2 hours."),
                hit(2, 102L, "Incident Review", "This incident recovered in 78 minutes.")
        ), 2000);

        assertThat(prompt.noEvidence()).isFalse();
        assertThat(prompt.userPrompt()).contains("only the numbered knowledge-base evidence");
        assertThat(prompt.userPrompt()).doesNotContain("proposed value");
    }

    @Test
    void shouldNotUseCorrectionPromptForOrdinaryDraftQuestions() {
        RagPrompt prompt = builder.build("What should draft customer updates exclude?", List.of(
                hit(1, 101L, "Communication Guide", "Draft updates should not include internal incident commander notes.")
        ), 2000);

        assertThat(prompt.noEvidence()).isFalse();
        assertThat(prompt.userPrompt()).contains("only the numbered knowledge-base evidence");
        assertThat(prompt.userPrompt()).doesNotContain("proposed value");
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
