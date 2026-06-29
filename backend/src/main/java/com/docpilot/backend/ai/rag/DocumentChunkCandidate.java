package com.docpilot.backend.ai.rag;

import java.util.LinkedHashMap;
import java.util.Map;

public record DocumentChunkCandidate(
        Long documentId,
        Long userId,
        int chunkIndex,
        String content,
        String contentHash,
        int startOffset,
        int endOffset,
        int tokenCount,
        String sectionTitle,
        int sectionOrdinal,
        String sectionPath,
        int sourceBlockOrdinal,
        String structureType,
        String qualityFlags
) {

    public DocumentChunkCandidate(Long documentId,
                                  Long userId,
                                  int chunkIndex,
                                  String content,
                                  String contentHash,
                                  int startOffset,
                                  int endOffset,
                                  int tokenCount) {
        this(documentId, userId, chunkIndex, content, contentHash, startOffset, endOffset, tokenCount,
                "", 0, "", chunkIndex, "paragraph", "none");
    }

    public DocumentChunkCandidate {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be non-negative");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("offsets must be non-negative and ordered");
        }
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must be non-negative");
        }
        sectionTitle = safeText(sectionTitle);
        if (sectionOrdinal < 0) {
            throw new IllegalArgumentException("sectionOrdinal must be non-negative");
        }
        sectionPath = safeText(sectionPath);
        if (sourceBlockOrdinal < 0) {
            throw new IllegalArgumentException("sourceBlockOrdinal must be non-negative");
        }
        structureType = safeText(structureType);
        if (structureType.isBlank()) {
            structureType = "paragraph";
        }
        qualityFlags = safeText(qualityFlags);
        if (qualityFlags.isBlank()) {
            qualityFlags = "none";
        }
    }

    public Map<String, String> structureMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("sectionTitle", sectionTitle);
        metadata.put("sectionOrdinal", String.valueOf(sectionOrdinal));
        metadata.put("sectionPath", sectionPath);
        metadata.put("sourceBlockOrdinal", String.valueOf(sourceBlockOrdinal));
        metadata.put("structureType", structureType);
        metadata.put("qualityFlags", qualityFlags);
        return metadata;
    }

    public DocumentChunkCandidate withQualityFlags(String resolvedQualityFlags) {
        return new DocumentChunkCandidate(
                documentId,
                userId,
                chunkIndex,
                content,
                contentHash,
                startOffset,
                endOffset,
                tokenCount,
                sectionTitle,
                sectionOrdinal,
                sectionPath,
                sourceBlockOrdinal,
                structureType,
                resolvedQualityFlags
        );
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
