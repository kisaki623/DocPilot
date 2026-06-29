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
}
