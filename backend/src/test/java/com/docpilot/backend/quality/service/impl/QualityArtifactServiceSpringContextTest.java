package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.service.QualityArtifactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class QualityArtifactServiceSpringContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(QualityArtifactServiceImpl.class);

    @Test
    void shouldLoadQualityArtifactServiceAsSpringBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(QualityArtifactService.class);
            assertThat(context.getBean(QualityArtifactService.class))
                    .isInstanceOf(QualityArtifactServiceImpl.class);
        });
    }
}
