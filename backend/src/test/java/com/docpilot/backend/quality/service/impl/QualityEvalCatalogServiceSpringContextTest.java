package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.service.QualityArtifactService;
import com.docpilot.backend.quality.service.QualityEvalCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QualityEvalCatalogServiceSpringContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(QualityArtifactService.class, () -> mock(QualityArtifactService.class))
            .withUserConfiguration(QualityEvalCatalogServiceImpl.class);

    @Test
    void shouldLoadQualityEvalCatalogServiceAsSpringBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(QualityEvalCatalogService.class);
            assertThat(context.getBean(QualityEvalCatalogService.class))
                    .isInstanceOf(QualityEvalCatalogServiceImpl.class);
        });
    }
}
