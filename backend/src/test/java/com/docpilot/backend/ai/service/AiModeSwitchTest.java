package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.service.impl.MockAiAnswerService;
import com.docpilot.backend.ai.service.impl.RealAiAnswerService;
import com.docpilot.backend.common.config.AiModeGuardConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiModeSwitchTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockAiAnswerService.class, RealAiAnswerService.class, AiModeGuardConfig.class);

    @Test
    void shouldLoadMockServiceWhenModeIsMock() {
        contextRunner.withPropertyValues("app.ai.mode=mock")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiAnswerService.class);
                    assertThat(context.getBean(AiAnswerService.class)).isInstanceOf(MockAiAnswerService.class);
                });
    }

    @Test
    void shouldLoadRealServiceWhenModeIsReal() {
        contextRunner.withPropertyValues(
                        "app.ai.mode=real",
                        "app.ai.real.api-key=test-key"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AiAnswerService.class);
                    assertThat(context.getBean(AiAnswerService.class)).isInstanceOf(RealAiAnswerService.class);
                });
    }

    @Test
    void shouldFailFastWhenModeValueIsInvalid() {
        contextRunner.withPropertyValues("app.ai.mode=abc")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Unsupported app.ai.mode='abc'. Allowed values: mock, real.");
                });
    }

    @Test
    void shouldFailFastWhenRealModeMissingApiKey() {
        contextRunner.withPropertyValues("app.ai.mode=real")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.ai.mode=real 时必须配置 AI_REAL_API_KEY");
                });
    }

    @Test
    void shouldFailFastWhenRealModeMissingModel() {
        contextRunner.withPropertyValues(
                        "app.ai.mode=real",
                        "app.ai.real.api-key=test-key",
                        "app.ai.real.model="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.ai.mode=real 时必须配置 AI_REAL_MODEL");
                });
    }

    @Test
    void shouldFailFastWhenSiliconFlowProviderBaseUrlIsNotSiliconFlow() {
        contextRunner.withPropertyValues(
                        "app.ai.mode=real",
                        "app.ai.real.api-key=test-key",
                        "app.ai.real.provider=siliconflow",
                        "app.ai.real.base-url=https://api.openai.com/v1"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("AI_REAL_PROVIDER=siliconflow 时，AI_REAL_BASE_URL 必须指向硅基流动 OpenAI 兼容网关，推荐值: https://api.siliconflow.cn/v1");
                });
    }

    @Test
    void shouldFailFastWhenRealModeProviderValueIsUnsupported() {
        contextRunner.withPropertyValues(
                        "app.ai.mode=real",
                        "app.ai.real.api-key=test-key",
                        "app.ai.real.provider=abc"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.ai.mode=real 时 AI_REAL_PROVIDER 仅支持 openai-compatible/siliconflow，当前值=abc");
                });
    }
}

