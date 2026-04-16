package com.docpilot.backend.file.storage;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

class FileStorageModeSwitchTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LocalFileStorageWriter.class, MinioFileStorageWriter.class, MockMinioClientConfig.class);

    @Test
    void shouldLoadLocalStorageWriterWhenModeIsLocal() {
        contextRunner
                .withPropertyValues(
                        "app.file.storage.mode=local",
                        "app.file.upload-dir=./data/test-uploads"
                )
                .run(context -> {
                    assertNotNull(context.getBean(FileStorageWriter.class));
                    assertNotNull(context.getBean(LocalFileStorageWriter.class));
                    org.junit.jupiter.api.Assertions.assertFalse(context.containsBean("minioFileStorageWriter"));
                });
    }

    @Test
    void shouldLoadMinioStorageWriterWhenModeIsMinio() {
        contextRunner
                .withPropertyValues(
                        "app.file.storage.mode=minio",
                        "app.file.storage.minio.endpoint=http://127.0.0.1:9000",
                        "app.file.storage.minio.access-key=minioadmin",
                        "app.file.storage.minio.secret-key=minioadmin",
                        "app.file.storage.minio.bucket=docpilot",
                        "app.file.storage.minio.base-path=uploads"
                )
                .run(context -> {
                    assertNotNull(context.getBean(MinioFileStorageWriter.class));
                    assertNotNull(context.getBean(FileStorageWriter.class));
                    org.junit.jupiter.api.Assertions.assertFalse(context.containsBean("localFileStorageWriter"));
                });
    }

    @Configuration
    static class MockMinioClientConfig {

        @Bean
        public MinioClient minioClient() throws Exception {
            MinioClient minioClient = Mockito.mock(MinioClient.class);
            Mockito.when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
            return minioClient;
        }
    }
}



