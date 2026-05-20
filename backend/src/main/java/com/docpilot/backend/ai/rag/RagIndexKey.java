package com.docpilot.backend.ai.rag;

public record RagIndexKey(
        Long documentId,
        String documentVersion
) {

    public static final String DEFAULT_VERSION = "default";

    public RagIndexKey {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        documentVersion = normalizeVersion(documentVersion);
    }

    public static RagIndexKey of(Long documentId, String documentVersion) {
        return new RagIndexKey(documentId, documentVersion);
    }

    private static String normalizeVersion(String documentVersion) {
        if (documentVersion == null || documentVersion.isBlank()) {
            return DEFAULT_VERSION;
        }
        return documentVersion.trim();
    }
}
