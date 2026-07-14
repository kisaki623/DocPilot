package com.docpilot.backend.file.storage;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FileContentReader {

    private final MinioFileStorageWriter minioFileStorageWriter;

    public FileContentReader(ObjectProvider<MinioFileStorageWriter> minioFileStorageWriterProvider) {
        this.minioFileStorageWriter = minioFileStorageWriterProvider.getIfAvailable();
    }

    public String readText(String storagePath) {
        return new String(readBytes(storagePath, Long.MAX_VALUE), StandardCharsets.UTF_8);
    }

    public byte[] readBytes(String storagePath, long maxBytes) {
        requireStoragePath(storagePath);
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        if (MinioFileStorageWriter.isMinioPath(storagePath)) {
            requireMinioWriter(storagePath);
            return minioFileStorageWriter.readBytes(storagePath, maxBytes);
        }
        try {
            Path path = Path.of(storagePath);
            if (!Files.exists(path)) {
                throw new IllegalStateException("source file does not exist");
            }
            long fileSize = Files.size(path);
            if (fileSize > maxBytes) {
                throw new IllegalStateException("source file exceeds parser size limit");
            }
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read source file", ex);
        }
    }

    public InputStream openStream(String storagePath) {
        requireStoragePath(storagePath);
        if (MinioFileStorageWriter.isMinioPath(storagePath)) {
            requireMinioWriter(storagePath);
            return minioFileStorageWriter.openStream(storagePath);
        }
        try {
            Path path = Path.of(storagePath);
            if (!Files.exists(path)) {
                throw new IllegalStateException("source file does not exist");
            }
            return Files.newInputStream(path);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to open source file", ex);
        }
    }

    private void requireStoragePath(String storagePath) {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            throw new IllegalStateException("storagePath must not be blank");
        }
    }

    private void requireMinioWriter(String storagePath) {
        if (minioFileStorageWriter == null) {
            throw new IllegalStateException("MinIO storage path detected but MinIO storage is not enabled: " + storagePath);
        }
    }
}
