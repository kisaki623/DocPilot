package com.docpilot.backend.ai.rag;

public record RagEvidenceCitation(
        int index,
        Long documentId,
        Integer indexVersion,
        Long chunkId,
        Integer chunkIndex,
        Integer startOffset,
        Integer endOffset,
        String contentHash,
        String snippet,
        String quoteText,
        Integer quoteStartOffset,
        Integer quoteEndOffset,
        double score
) {

    public RagEvidenceCitation {
        if (index <= 0) {
            throw new IllegalArgumentException("index must be positive");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (indexVersion == null || indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive");
        }
        if (chunkIndex == null || chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        contentHash = contentHash == null ? "" : contentHash.trim();
        snippet = snippet == null ? "" : snippet.trim();
        quoteText = quoteText == null ? "" : quoteText.trim();
    }
}
