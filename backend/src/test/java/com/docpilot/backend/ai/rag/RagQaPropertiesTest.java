package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RagQaPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(RagQaProperties.class)
            .withPropertyValues(
                    "app.rag.qa.enabled=false",
                    "app.rag.qa.top-k=3",
                    "app.rag.qa.max-context-chars=2000",
                    "app.rag.qa.fallback-enabled=true"
            );

    @Test
    void shouldKeepQaRagDisabledByDefault() {
        contextRunner.run(context -> {
            RagQaProperties properties = context.getBean(RagQaProperties.class);

            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getTopK()).isEqualTo(3);
            assertThat(properties.getMaxContextChars()).isEqualTo(2000);
            assertThat(properties.isFallbackEnabled()).isTrue();
        });
    }

    @Test
    void shouldBindEnabledQaRagProperties() {
        contextRunner.withPropertyValues(
                        "app.rag.qa.enabled=true",
                        "app.rag.qa.top-k=2",
                        "app.rag.qa.max-context-chars=512",
                        "app.rag.qa.fallback-enabled=false"
                )
                .run(context -> {
                    RagQaProperties properties = context.getBean(RagQaProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getTopK()).isEqualTo(2);
                    assertThat(properties.getMaxContextChars()).isEqualTo(512);
                    assertThat(properties.isFallbackEnabled()).isFalse();
                });
    }

    @Test
    void shouldRejectNonPositiveTopKAndMaxContextChars() {
        contextRunner.withPropertyValues("app.rag.qa.top-k=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.rag.qa.top-k must be positive.");
                });

        contextRunner.withPropertyValues("app.rag.qa.max-context-chars=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.rag.qa.max-context-chars must be positive.");
                });
    }
}
