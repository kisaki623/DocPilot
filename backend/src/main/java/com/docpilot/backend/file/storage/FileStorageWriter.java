package com.docpilot.backend.file.storage;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageWriter {

    String store(MultipartFile file, String extension);

    String store(Path localFile, String extension);

    void deleteQuietly(String storagePath);
}

