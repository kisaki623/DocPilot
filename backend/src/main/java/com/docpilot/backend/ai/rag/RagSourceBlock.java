package com.docpilot.backend.ai.rag;

public record RagSourceBlock(
        int blockIndex,
        String blockType,
        Integer pageNumber,
        String sectionTitle,
        String sectionPath,
        int startOffset,
        int endOffset,
        String sourceLocator
) {

    public RagSourceBlock {
        if (blockIndex < 0) {
            throw new IllegalArgumentException("blockIndex must be non-negative");
        }
        blockType = blockType == null ? "" : blockType.trim();
        sectionTitle = sectionTitle == null ? "" : sectionTitle.trim();
        sectionPath = sectionPath == null ? "" : sectionPath.trim();
        sourceLocator = sourceLocator == null ? "" : sourceLocator.trim();
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("offsets must be non-negative and ordered");
        }
    }
}
