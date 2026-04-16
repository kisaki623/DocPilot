package com.docpilot.backend.common.config;

import com.docpilot.backend.ai.service.AiAnswerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiModeGuardConfig {

    @Bean
    @ConditionalOnMissingBean(AiAnswerService.class)
    public AiAnswerService unsupportedAiAnswerService(@Value("${app.ai.mode:mock}") String mode) {
        throw new IllegalStateException("Unsupported app.ai.mode='" + mode + "'. Allowed values: mock, real.");
    }
}

