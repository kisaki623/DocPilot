package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RagVectorStorePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(RagVectorStoreProperties.class)
            .withPropertyValues(
                    "app.rag.vector-store.provider=in_memory",
                    "app.rag.vector-store.qdrant.collection=docpilot_rag_demo",
                    "app.rag.vector-store.qdrant.endpoint=",
                    "app.rag.vector-store.qdrant.api-key=",
                    "app.rag.vector-store.qdrant.connect-timeout-ms=5000",
                    "app.rag.vector-store.qdrant.request-timeout-ms=30000"
            );

    @Test
    void shouldUseInMemoryProviderByDefault() {
        contextRunner.run(context -> {
            RagVectorStoreProperties properties = context.getBean(RagVectorStoreProperties.class);

            assertThat(properties.getProvider()).isEqualTo(RagVectorStoreProperties.PROVIDER_IN_MEMORY);
            assertThat(properties.isInMemoryProvider()).isTrue();
            assertThat(properties.isQdrantDisabledProvider()).isFalse();
            assertThat(properties.isQdrantProvider()).isFalse();
            assertThat(properties.getQdrant().getCollection()).isEqualTo("docpilot_rag_demo");
            assertThat(properties.getQdrant().getEndpoint()).isEmpty();
            assertThat(properties.getQdrant().getApiKey()).isEmpty();
            assertThat(properties.getQdrant().getConnectTimeoutMs()).isEqualTo(5000);
            assertThat(properties.getQdrant().getRequestTimeoutMs()).isEqualTo(30000);
        });
    }

    @Test
    void shouldBindQdrantDisabledProviderWithoutEndpoint() {
        contextRunner.withPropertyValues("app.rag.vector-store.provider=qdrant_disabled")
                .run(context -> {
                    RagVectorStoreProperties properties = context.getBean(RagVectorStoreProperties.class);

                    assertThat(properties.getProvider()).isEqualTo(RagVectorStoreProperties.PROVIDER_QDRANT_DISABLED);
                    assertThat(properties.isQdrantDisabledProvider()).isTrue();
                    assertThat(properties.getQdrant().getEndpoint()).isEmpty();
                });
    }

    @Test
    void shouldBindExplicitQdrantProvider() {
        contextRunner.withPropertyValues("app.rag.vector-store.provider=qdrant")
                .run(context -> {
                    RagVectorStoreProperties properties = context.getBean(RagVectorStoreProperties.class);

                    assertThat(properties.getProvider()).isEqualTo(RagVectorStoreProperties.PROVIDER_QDRANT);
                    assertThat(properties.isQdrantProvider()).isTrue();
                });
    }

    @Test
    void shouldNormalizeProviderAliases() {
        assertProviderAlias("in-memory", RagVectorStoreProperties.PROVIDER_IN_MEMORY);
        assertProviderAlias("inMemory", RagVectorStoreProperties.PROVIDER_IN_MEMORY);
        assertProviderAlias("qdrant-disabled", RagVectorStoreProperties.PROVIDER_QDRANT_DISABLED);
        assertProviderAlias("qdrantDisabled", RagVectorStoreProperties.PROVIDER_QDRANT_DISABLED);
    }

    @Test
    void shouldRejectUnsupportedProvider() {
        contextRunner.withPropertyValues("app.rag.vector-store.provider=definitely_unknown")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Unsupported app.rag.vector-store.provider='definitely_unknown'. Allowed values: in_memory, qdrant_disabled, qdrant.");
                });
    }

    @Test
    void shouldRejectNonPositiveQdrantTimeouts() {
        contextRunner.withPropertyValues("app.rag.vector-store.qdrant.connect-timeout-ms=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.rag.vector-store.qdrant.connect-timeout-ms must be positive.");
                });

        contextRunner.withPropertyValues("app.rag.vector-store.qdrant.request-timeout-ms=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.rag.vector-store.qdrant.request-timeout-ms must be positive.");
                });
    }

    private void assertProviderAlias(String provider, String expectedProvider) {
        contextRunner.withPropertyValues("app.rag.vector-store.provider=" + provider)
                .run(context -> {
                    RagVectorStoreProperties properties = context.getBean(RagVectorStoreProperties.class);

                    assertThat(properties.getProvider()).isEqualTo(expectedProvider);
                });
    }
}
