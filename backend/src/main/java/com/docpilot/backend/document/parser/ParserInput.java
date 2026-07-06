package com.docpilot.backend.document.parser;

import java.util.Locale;

public record ParserInput(
        Long documentId,
        Long fileRecordId,
        String fileName,
        String fileExt,
        String contentType,
        Long fileSize,
        String storagePath,
        ParserOptions options
) {

    public ParserInput {
        fileName = safe(fileName);
        fileExt = safe(fileExt).toLowerCase(Locale.ROOT);
        contentType = safe(contentType).toLowerCase(Locale.ROOT);
        storagePath = safe(storagePath);
        options = options == null ? ParserOptions.defaults() : options;
    }

    public boolean hasExtension(String extension) {
        return fileExt.equalsIgnoreCase(extension);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
