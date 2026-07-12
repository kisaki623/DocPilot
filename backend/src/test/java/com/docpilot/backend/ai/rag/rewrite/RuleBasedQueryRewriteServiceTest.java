package com.docpilot.backend.ai.rag.rewrite;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedQueryRewriteServiceTest {

    private final RuleBasedQueryRewriteService service = new RuleBasedQueryRewriteService();

    @Test
    void shouldKeepOriginalQueryFirstAndGenerateBoundedVariants() {
        List<QueryRewriteVariant> variants = service.rewrite(
                "Explain cache invalidation and vector retention policy?",
                4
        );

        assertThat(variants).hasSize(4);
        assertThat(variants.get(0).query()).isEqualTo("Explain cache invalidation and vector retention policy?");
        assertThat(variants.get(0).strategy()).isEqualTo("original");
        assertThat(variants).extracting(QueryRewriteVariant::query)
                .contains("cache invalidation and vector retention policy", "cache invalidation", "vector retention policy");
        assertThat(variants).extracting(QueryRewriteVariant::ordinal).containsExactly(0, 1, 2, 3);
    }

    @Test
    void shouldDeduplicateCleanedQueryVariants() {
        List<QueryRewriteVariant> variants = service.rewrite("cache invalidation", 3);

        assertThat(variants).hasSize(1);
        assertThat(variants.get(0).query()).isEqualTo("cache invalidation");
    }

    @Test
    void shouldGenerateEnglishDomainTermsForChineseComplianceQuery() {
        List<QueryRewriteVariant> variants = service.rewrite(
                "哪条政策证据说明合规导出检查点在进入审计留存证明前需要法律审核？",
                5
        );

        assertThat(variants.get(0).query()).contains("合规导出检查点");
        assertThat(variants).extracting(QueryRewriteVariant::strategy)
                .contains("chinese_domain_terms");
        assertThat(variants).extracting(QueryRewriteVariant::query)
                .anySatisfy(query -> assertThat(query)
                        .contains("compliance")
                        .contains("export")
                        .contains("checkpoint")
                        .contains("audit")
                        .contains("retention")
                        .contains("legal")
                        .contains("review"));
    }

    @Test
    void shouldGenerateEnglishDomainTermsForChineseFinanceQuery() {
        List<QueryRewriteVariant> variants = service.rewrite(
                "报销由谁审批，发票档案保留多久？",
                5
        );

        assertThat(variants).extracting(QueryRewriteVariant::query)
                .anySatisfy(query -> assertThat(query)
                        .contains("expense")
                        .contains("reimbursement")
                        .contains("approval")
                        .contains("invoice")
                        .contains("archives")
                        .contains("retention")
                        .contains("period"));
    }
}
