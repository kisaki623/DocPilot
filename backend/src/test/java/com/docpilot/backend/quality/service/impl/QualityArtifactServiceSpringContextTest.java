package com.docpilot.backend.quality.service.impl;

import com.docpilot.backend.quality.mapper.QualityImportEventMapper;
import com.docpilot.backend.quality.mapper.QualityRunCaseMapper;
import com.docpilot.backend.quality.mapper.QualityRunGateMapper;
import com.docpilot.backend.quality.mapper.QualityRunMapper;
import com.docpilot.backend.quality.service.QualityArtifactImportService;
import com.docpilot.backend.quality.service.QualityArtifactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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

    @Test
    void shouldLoadQualityArtifactImportServiceAsSpringBean() {
        new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(QualityRunMapper.class, () -> mock(QualityRunMapper.class))
                .withBean(QualityRunGateMapper.class, () -> mock(QualityRunGateMapper.class))
                .withBean(QualityRunCaseMapper.class, () -> mock(QualityRunCaseMapper.class))
                .withBean(QualityImportEventMapper.class, () -> mock(QualityImportEventMapper.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withUserConfiguration(QualityArtifactServiceImpl.class, QualityArtifactImportServiceImpl.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(QualityArtifactImportService.class);
                    assertThat(context.getBean(QualityArtifactImportService.class))
                            .isInstanceOf(QualityArtifactImportServiceImpl.class);
                });
    }
}
