package com.docpilot.backend.file.storage;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.file.storage", name = "mode", havingValue = "minio")
public class MinioFileStorageWriter implements FileStorageWriter {

    private static final String MINIO_PREFIX = "minio://";

    private final MinioClient minioClient;
    private final String bucket;
    private final String basePath;

    public MinioFileStorageWriter(MinioClient minioClient,
                                  @Value("${app.file.storage.minio.bucket:docpilot}") String bucket,
                                  @Value("${app.file.storage.minio.base-path:uploads}") String basePath) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.basePath = basePath;
    }

    @PostConstruct
    public void ensureBucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("MinIO bucket must not be blank");
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO bucket initialization failed", ex);
        }
    }

    @Override
    public String store(MultipartFile file, String extension) {
        String objectName = buildObjectName(extension);
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return toStoragePath(bucket, objectName);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "upload to MinIO failed");
        }
    }

    @Override
    public String store(Path localFile, String extension) {
        String objectName = buildObjectName(extension);
        try (InputStream inputStream = Files.newInputStream(localFile, StandardOpenOption.READ)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(inputStream, Files.size(localFile), -1)
                            .contentType("application/octet-stream")
                            .build()
            );
            return toStoragePath(bucket, objectName);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "upload to MinIO failed");
        }
    }

    @Override
    public void deleteQuietly(String storagePath) {
        // Keep minimal: duplicate file cleanup in MinIO is optional in this stage.
    }

    public String readText(String storagePath) {
        return new String(readBytes(storagePath, Long.MAX_VALUE), StandardCharsets.UTF_8);
    }

    public byte[] readBytes(String storagePath, long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        try (InputStream inputStream = openStream(storagePath)) {
            byte[] bytes = inputStream.readAllBytes();
            if (bytes.length > maxBytes) {
                throw new IllegalStateException("MinIO object exceeds parser size limit");
            }
            return bytes;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read MinIO object", ex);
        }
    }

    public InputStream openStream(String storagePath) {
        MinioObjectAddress address = parse(storagePath);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder().bucket(address.bucket()).object(address.objectName()).build());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to open MinIO object", ex);
        }
    }

    public static boolean isMinioPath(String storagePath) {
        return storagePath != null && storagePath.startsWith(MINIO_PREFIX);
    }

    public static MinioObjectAddress parse(String storagePath) {
        if (!isMinioPath(storagePath)) {
            throw new IllegalArgumentException("invalid MinIO storage path");
        }
        String raw = storagePath.substring(MINIO_PREFIX.length());
        int split = raw.indexOf('/');
        if (split <= 0 || split == raw.length() - 1) {
            throw new IllegalArgumentException("invalid MinIO storage path");
        }
        return new MinioObjectAddress(raw.substring(0, split), raw.substring(split + 1));
    }

    private String buildObjectName(String extension) {
        String dateFolder = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String normalizedBasePath = (basePath == null || basePath.isBlank()) ? "uploads" : basePath;
        return normalizedBasePath + "/" + dateFolder + "/" + UUID.randomUUID() + "." + extension;
    }

    private String toStoragePath(String bucketName, String objectName) {
        return MINIO_PREFIX + bucketName + "/" + objectName;
    }

    public record MinioObjectAddress(String bucket, String objectName) {
    }
}
