package com.docpilot.backend.file.storage;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.file.storage", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageWriter implements FileStorageWriter {

    private final String uploadDir;

    public LocalFileStorageWriter(@Value("${app.file.upload-dir:./data/uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public String store(MultipartFile file, String extension) {
        String dateFolder = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String storedName = UUID.randomUUID() + "." + extension;
        Path targetDir = Paths.get(uploadDir, dateFolder).toAbsolutePath().normalize();
        Path targetFile = targetDir.resolve(storedName).normalize();

        try {
            Files.createDirectories(targetDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return targetFile.toString();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "文件存储到本地目录失败");
        }
    }

    @Override
    public String store(Path localFile, String extension) {
        String dateFolder = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String storedName = UUID.randomUUID() + "." + extension;
        Path targetDir = Paths.get(uploadDir, dateFolder).toAbsolutePath().normalize();
        Path targetFile = targetDir.resolve(storedName).normalize();

        try {
            Files.createDirectories(targetDir);
            Files.copy(localFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return targetFile.toString();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "文件存储到本地目录失败");
        }
    }

    @Override
    public void deleteQuietly(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(storagePath));
        } catch (IOException ignored) {
            // Ignore cleanup failure in minimal upload path.
        }
    }
}

