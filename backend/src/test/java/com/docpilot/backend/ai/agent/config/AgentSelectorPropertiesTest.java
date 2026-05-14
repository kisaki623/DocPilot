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
            assertThat(properties.isShadowLlmMode()).isFalse();
        });
    }

    @Test
    void shouldBindShadowLlmModeAndShadowEnabledFlag() {
        contextRunner.withPropertyValues(
                        "app.agent.selector.mode=shadow_llm",
                        "app.agent.selector.shadow-enabled=true"
                )
                .run(context -> {
                    AgentSelectorProperties properties = context.getBean(AgentSelectorProperties.class);

                    assertThat(properties.getMode()).isEqualTo("shadow_llm");
                    assertThat(properties.isShadowEnabled()).isTrue();
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
}
