package com.docpilot.backend.document.parser;

public record ParserOptions(
        long maxFileSizeBytes,
        long timeoutMillis
) {

    public static ParserOptions defaults() {
        return new ParserOptions(20L * 1024L * 1024L, 10_000L);
    }

    public ParserOptions {
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("maxFileSizeBytes must be positive");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
    }
}
