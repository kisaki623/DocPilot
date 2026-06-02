package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RagEmbeddingPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(RagEmbeddingProperties.class)
            .withPropertyValues(
                    "app.rag.embedding.provider=fake",
                    "app.rag.embedding.enabled=true",
                    "app.rag.embedding.base-url=",
                    "app.rag.embedding.model=",
                    "app.rag.embedding.api-key=",
                    "app.rag.embedding.connect-timeout-ms=5000",
                    "app.rag.embedding.request-timeout-ms=30000",
                    "app.rag.embedding.dimension=32"
            );

    @Test
    void shouldUseFakeProviderByDefault() {
        contextRunner.run(context -> {
            RagEmbeddingProperties properties = context.getBean(RagEmbeddingProperties.class);

            assertThat(properties.getProvider()).isEqualTo(RagEmbeddingProperties.PROVIDER_FAKE);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.isFakeProvider()).isTrue();
            assertThat(properties.isDisabledProvider()).isFalse();
            assertThat(properties.isOpenAiCompatibleProvider()).isFalse();
            assertThat(properties.getBaseUrl()).isEmpty();
            assertThat(properties.getModel()).isEmpty();
            assertThat(properties.getApiKey()).isEmpty();
            assertThat(properties.getConnectTimeoutMs()).isEqualTo(5000);
            assertThat(properties.getRequestTimeoutMs()).isEqualTo(30000);
            assertThat(properties.getDimension()).isEqualTo(32);
        });
    }

    @Test
    void shouldBindDisabledProvider() {
        contextRunner.withPropertyValues("app.rag.embedding.provider=disabled")
                .run(context -> {
                    RagEmbeddingProperties properties = context.getBean(RagEmbeddingProperties.class);

                    assertThat(properties.getProvider()).isEqualTo(RagEmbeddingProperties.PROVIDER_DISABLED);
                    assertThat(properties.isDisabledProvider()).isTrue();
                });
    }

    @Test
    void shouldTreatDisabledEnabledFlagAsDisabledProvider() {
        contextRunner.withPropertyValues("app.rag.embedding.enabled=false")
                .run(context -> {
                    RagEmbeddingProperties properties = context.getBean(RagEmbeddingProperties.class);

                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.isDisabledProvider()).isTrue();
                });
    }

    @Test
    void shouldNormalizeOpenAiCompatibleProviderAliases() {
        assertMockAlias("mock");
        assertProviderAlias("openai_compatible");
        assertProviderAlias("openai-compatible");
        assertProviderAlias("openaiCompatible");
    }

    @Test
    void shouldRejectUnsupportedProvider() {
        contextRunner.withPropertyValues("app.rag.embedding.provider=qdrant")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Unsupported app.rag.embedding.provider='qdrant'. Allowed values: disabled, fake, mock, openai_compatible.");
                });
    }

    @Test
    void shouldRejectNonPositiveTimeoutAndDimension() {
        contextRunner.withPropertyValues("app.rag.embedding.connect-timeout-ms=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.rag.embedding.connect-timeout-ms must be positive.");
                });

        contextRunner.withPropertyValues("app.rag.embedding.request-timeout-ms=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.rag.embedding.request-timeout-ms must be positive.");
                });

        contextRunner.withPropertyValues("app.rag.embedding.dimension=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.rag.embedding.dimension must be positive.");
                });
    }

    @Test
    void shouldConvertToCanonicalEmbeddingProperties() {
        RagEmbeddingProperties properties = new RagEmbeddingProperties();
        properties.setProvider("fake");
        properties.setModel("mock-model");
        properties.setDimension(16);

        EmbeddingProperties embeddingProperties = properties.toEmbeddingProperties();

        assertThat(embeddingProperties.getProvider()).isEqualTo(EmbeddingProperties.PROVIDER_MOCK);
        assertThat(embeddingProperties.getModel()).isEqualTo("mock-model");
        assertThat(embeddingProperties.getDimension()).isEqualTo(16);
    }

    private void assertMockAlias(String provider) {
        contextRunner.withPropertyValues("app.rag.embedding.provider=" + provider)
                .run(context -> {
                    RagEmbeddingProperties properties = context.getBean(RagEmbeddingProperties.class);

                    assertThat(properties.getProvider()).isEqualTo(RagEmbeddingProperties.PROVIDER_MOCK);
                    assertThat(properties.isFakeProvider()).isTrue();
                });
    }

    private void assertProviderAlias(String provider) {
        contextRunner.withPropertyValues("app.rag.embedding.provider=" + provider)
                .run(context -> {
                    RagEmbeddingProperties properties = context.getBean(RagEmbeddingProperties.class);

                    assertThat(properties.getProvider()).isEqualTo(RagEmbeddingProperties.PROVIDER_OPENAI_COMPATIBLE);
                    assertThat(properties.isOpenAiCompatibleProvider()).isTrue();
                });
    }
}
