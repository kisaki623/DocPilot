package com.docpilot.backend.common.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioStorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.file.storage", name = "mode", havingValue = "minio")
    public MinioClient minioClient(@Value("${app.file.storage.minio.endpoint:}") String endpoint,
                                   @Value("${app.file.storage.minio.access-key:}") String accessKey,
                                   @Value("${app.file.storage.minio.secret-key:}") String secretKey) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("MinIO endpoint 未配置，请检查 app.file.storage.minio.endpoint");
        }
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalStateException("MinIO accessKey 未配置，请检查 app.file.storage.minio.access-key");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("MinIO secretKey 未配置，请检查 app.file.storage.minio.secret-key");
        }
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}


