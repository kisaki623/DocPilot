package com.docpilot.backend.file.storage;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FileContentReader {

    private final MinioFileStorageWriter minioFileStorageWriter;

    public FileContentReader(org.springframework.beans.factory.ObjectProvider<MinioFileStorageWriter> minioFileStorageWriterProvider) {
        this.minioFileStorageWriter = minioFileStorageWriterProvider.getIfAvailable();
    }

    public String readText(String storagePath) {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            throw new IllegalStateException("storagePath 为空");
        }
        if (MinioFileStorageWriter.isMinioPath(storagePath)) {
            if (minioFileStorageWriter == null) {
                throw new IllegalStateException("检测到 MinIO 存储路径，但当前未启用 MinIO 模式: " + storagePath);
            }
            return minioFileStorageWriter.readText(storagePath);
        }
        try {
            Path path = Path.of(storagePath);
            if (!Files.exists(path)) {
                throw new IllegalStateException("源文件不存在: " + storagePath);
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取源文件失败: " + storagePath, ex);
        }
    }
}

