package com.docpilot.backend.ai.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSelectorPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(AgentSelectorProperties.class);

    @Test
    void shouldUseKeywordModeByDefault() {
        contextRunner.run(context -> {
            AgentSelectorProperties properties = context.getBean(AgentSelectorProperties.class);

            assertThat(properties.getMode()).isEqualTo("keyword");
            assertThat(properties.isShadowEnabled()).isFalse();
            assertThat(properties.isRealShadowEnabled()).isFalse();
            assertThat(properties.isRealShadowRecordMetrics()).isFalse();
            assertThat(properties.isRealShadowFailOpen()).isTrue();
            assertThat(properties.getLlmProvider()).isEqualTo("disabled");
            assertThat(properties.getLlmModel()).isEmpty();
            assertThat(properties.getLlmBaseUrl()).isEmpty();
            assertThat(properties.getLlmRequestTimeoutMs()).isEqualTo(3000);
            assertThat(properties.isShadowLlmMode()).isFalse();
        });
    }

    @Test
    void shouldBindShadowLlmModeAndShadowEnabledFlag() {
        contextRunner.withPropertyValues(
                        "app.agent.selector.mode=shadow_llm",
                        "app.agent.selector.shadow-enabled=true",
                        "app.agent.selector.real-shadow-enabled=true",
                        "app.agent.selector.real-shadow-record-metrics=true",
                        "app.agent.selector.real-shadow-fail-open=false",
                        "app.agent.selector.llm-provider=fake",
                        "app.agent.selector.llm-model=fake-selector",
                        "app.agent.selector.llm-base-url=https://example.invalid/v1",
                        "app.agent.selector.llm-request-timeout-ms=5000"
                )
                .run(context -> {
                    AgentSelectorProperties properties = context.getBean(AgentSelectorProperties.class);

                    assertThat(properties.getMode()).isEqualTo("shadow_llm");
                    assertThat(properties.isShadowEnabled()).isTrue();
                    assertThat(properties.isRealShadowEnabled()).isTrue();
                    assertThat(properties.isRealShadowRecordMetrics()).isTrue();
                    assertThat(properties.isRealShadowFailOpen()).isFalse();
                    assertThat(properties.getLlmProvider()).isEqualTo("fake");
                    assertThat(properties.getLlmModel()).isEqualTo("fake-selector");
                    assertThat(properties.getLlmBaseUrl()).isEqualTo("https://example.invalid/v1");
                    assertThat(properties.getLlmRequestTimeoutMs()).isEqualTo(5000);
                    assertThat(properties.isShadowLlmMode()).isTrue();
                });
    }

    @Test
    void shouldRejectUnsupportedMode() {
        contextRunner.withPropertyValues("app.agent.selector.mode=real_llm")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Unsupported app.agent.selector.mode='real_llm'. Allowed values: keyword, shadow_llm.");
                });
    }

    @Test
    void shouldRejectUnsupportedProvider() {
        contextRunner.withPropertyValues("app.agent.selector.llm-provider=real_network")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Unsupported app.agent.selector.llm-provider='real_network'. Allowed values: disabled, fake, openai_compatible.");
                });
    }

    @Test
    void shouldRejectNonPositiveTimeout() {
        contextRunner.withPropertyValues("app.agent.selector.llm-request-timeout-ms=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.agent.selector.llm-request-timeout-ms must be positive.");
                });
    }
}
